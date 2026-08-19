package edu.batchmaker.service.solver;

import edu.batchmaker.dto.solver.SolverPayload;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * In-JVM safety net used only when the CP-SAT service is unreachable.
 *
 * <p>This is a greedy first-fit heuristic, not an optimizer: it respects every
 * hard constraint but makes no claim about schedule quality, and results are
 * flagged so a coordinator knows the real engine did not run. The CP-SAT
 * service remains the system's primary and intended optimizer.
 */
@Slf4j
@Component
public class FallbackScheduler {

    private record Placement(String day, List<Long> slotIds, int startMinute, int endMinute) {
    }

    private record Booked(String day, int start, int end, Long facultyId, Long labId, Long practicalId) {
        boolean overlaps(String otherDay, int otherStart, int otherEnd) {
            return day.equals(otherDay) && start < otherEnd && otherStart < end;
        }
    }

    public SolverPayload.Response solve(SolverPayload.Request request) {
        long started = System.currentTimeMillis();

        List<SolverPayload.TimeSlotIn> slots = request.timeSlots().stream()
                .sorted(Comparator.comparing(SolverPayload.TimeSlotIn::order))
                .toList();
        List<Placement> placements = enumeratePlacements(slots, request.days());

        Map<Long, Set<String>> facultyBlocked = blockedCells(request.faculty(),
                SolverPayload.FacultyIn::id, SolverPayload.FacultyIn::unavailable);
        Map<Long, Set<String>> labBlocked = blockedCells(request.labs(),
                SolverPayload.LabIn::id, SolverPayload.LabIn::unavailable);

        Map<Long, List<Long>> conflictsWith = new HashMap<>();
        for (List<Long> group : request.studentConflictGroups()) {
            for (Long member : group) {
                for (Long other : group) {
                    if (!member.equals(other)) {
                        conflictsWith.computeIfAbsent(member, k -> new ArrayList<>()).add(other);
                    }
                }
            }
        }

        Map<Long, Integer> facultyLoad = new HashMap<>();
        List<Booked> booked = new ArrayList<>();
        List<SolverPayload.AssignmentOut> assignments = new ArrayList<>();
        List<SolverPayload.ViolationOut> violations = new ArrayList<>();

        // Hardest practicals first: fewest eligible labs, then fewest faculty.
        List<SolverPayload.PracticalIn> ordered = new ArrayList<>(request.practicals());
        ordered.sort(Comparator
                .comparingInt((SolverPayload.PracticalIn p) -> eligibleLabs(request, p).size())
                .thenComparingInt(p -> eligibleFaculty(request, p).size()));

        for (SolverPayload.PracticalIn practical : ordered) {
            List<SolverPayload.LabIn> labs = eligibleLabs(request, practical);
            List<SolverPayload.FacultyIn> faculty = eligibleFaculty(request, practical);

            if (labs.isEmpty() || faculty.isEmpty()) {
                violations.add(new SolverPayload.ViolationOut(
                        labs.isEmpty() ? "NO_SUITABLE_LAB" : "NO_QUALIFIED_FACULTY",
                        labs.isEmpty()
                                ? "No available laboratory of type '" + practical.requiredLabType()
                                        + "' can seat " + practical.studentCount() + " students."
                                : "No active faculty member is qualified for subject " + practical.subjectId() + ".",
                        practical.id()));
                continue;
            }

            int sessions = practical.sessionsPerWeek() == null ? 1 : Math.max(1, practical.sessionsPerWeek());
            Set<String> usedDays = new HashSet<>();

            for (int session = 0; session < sessions; session++) {
                boolean placed = false;

                for (Placement placement : placements) {
                    if (placement.endMinute() - placement.startMinute() != practical.durationMinutes()) {
                        continue;
                    }
                    // Spread repeated sessions across the week where possible.
                    if (sessions > 1 && usedDays.contains(placement.day()) && usedDays.size() < request.days().size()) {
                        continue;
                    }
                    if (clashesWithCohort(booked, conflictsWith, practical.id(), placement)) {
                        continue;
                    }

                    // Least-loaded qualified faculty first, to keep workload even.
                    List<SolverPayload.FacultyIn> candidates = new ArrayList<>(faculty);
                    candidates.sort(Comparator.comparingInt(f -> facultyLoad.getOrDefault(f.id(), 0)));

                    for (SolverPayload.FacultyIn member : candidates) {
                        if (blocked(facultyBlocked, member.id(), placement)) {
                            continue;
                        }
                        if (busy(booked, placement, b -> Objects.equals(b.facultyId(), member.id()))) {
                            continue;
                        }
                        for (SolverPayload.LabIn lab : labs) {
                            if (blocked(labBlocked, lab.id(), placement)) {
                                continue;
                            }
                            if (busy(booked, placement, b -> Objects.equals(b.labId(), lab.id()))) {
                                continue;
                            }

                            booked.add(new Booked(placement.day(), placement.startMinute(),
                                    placement.endMinute(), member.id(), lab.id(), practical.id()));
                            facultyLoad.merge(member.id(), practical.durationMinutes(), Integer::sum);
                            usedDays.add(placement.day());

                            assignments.add(new SolverPayload.AssignmentOut(
                                    practical.id(), session, practical.batchId(), practical.subjectId(),
                                    member.id(), lab.id(), placement.day(),
                                    placement.slotIds().get(0),
                                    placement.slotIds().get(placement.slotIds().size() - 1),
                                    format(placement.startMinute()), format(placement.endMinute())));
                            placed = true;
                            break;
                        }
                        if (placed) {
                            break;
                        }
                    }
                    if (placed) {
                        break;
                    }
                }

                if (!placed) {
                    violations.add(new SolverPayload.ViolationOut("NO_FEASIBLE_PLACEMENT",
                            "No free slot could be found for practical " + practical.id()
                                    + " (session " + (session + 1) + ").", practical.id()));
                }
            }
        }

        long runtime = System.currentTimeMillis() - started;
        if (!violations.isEmpty()) {
            return new SolverPayload.Response("INFEASIBLE", 0.0, "builtin-greedy-fallback", runtime,
                    List.of(), violations, List.of(), null);
        }

        return new SolverPayload.Response(
                "SUCCESS",
                estimateScore(facultyLoad),
                "builtin-greedy-fallback",
                runtime,
                assignments,
                List.of(),
                List.of("The optimisation service was unavailable, so a greedy fallback scheduler produced "
                        + "this timetable. It satisfies all hard constraints but is not optimised. "
                        + "Regenerate once the optimisation service is running."),
                null);
    }

    private boolean clashesWithCohort(List<Booked> booked, Map<Long, List<Long>> conflictsWith,
                                      Long practicalId, Placement placement) {
        List<Long> conflicting = conflictsWith.getOrDefault(practicalId, List.of());
        return booked.stream().anyMatch(b ->
                (conflicting.contains(b.practicalId()) || Objects.equals(b.practicalId(), practicalId))
                        && b.overlaps(placement.day(), placement.startMinute(), placement.endMinute()));
    }

    private static boolean busy(List<Booked> booked, Placement placement, java.util.function.Predicate<Booked> match) {
        return booked.stream()
                .filter(match)
                .anyMatch(b -> b.overlaps(placement.day(), placement.startMinute(), placement.endMinute()));
    }

    private static boolean blocked(Map<Long, Set<String>> blockedCells, Long id, Placement placement) {
        Set<String> cells = blockedCells.getOrDefault(id, Set.of());
        return placement.slotIds().stream().anyMatch(slotId -> cells.contains(placement.day() + "|" + slotId));
    }

    private <T> Map<Long, Set<String>> blockedCells(List<T> items,
                                                    java.util.function.Function<T, Long> idOf,
                                                    java.util.function.Function<T, List<List<Object>>> cellsOf) {
        Map<Long, Set<String>> result = new HashMap<>();
        for (T item : items) {
            Set<String> cells = new HashSet<>();
            List<List<Object>> raw = cellsOf.apply(item);
            if (raw != null) {
                raw.forEach(cell -> cells.add(cell.get(0) + "|" + cell.get(1)));
            }
            result.put(idOf.apply(item), cells);
        }
        return result;
    }

    private static List<SolverPayload.LabIn> eligibleLabs(SolverPayload.Request request,
                                                          SolverPayload.PracticalIn practical) {
        return request.labs().stream()
                .filter(l -> l.labType().equals(practical.requiredLabType()))
                .filter(l -> l.capacity() >= practical.studentCount())
                .sorted(Comparator.comparingInt(SolverPayload.LabIn::capacity))
                .toList();
    }

    private static List<SolverPayload.FacultyIn> eligibleFaculty(SolverPayload.Request request,
                                                                 SolverPayload.PracticalIn practical) {
        return request.faculty().stream()
                .filter(f -> f.qualifiedSubjectIds() != null
                        && f.qualifiedSubjectIds().contains(practical.subjectId()))
                .toList();
    }

    private static List<Placement> enumeratePlacements(List<SolverPayload.TimeSlotIn> slots, List<String> days) {
        List<Placement> placements = new ArrayList<>();
        for (String day : days) {
            for (int i = 0; i < slots.size(); i++) {
                if (!slots.get(i).teachable()) {
                    continue;
                }
                List<Long> covered = new ArrayList<>();
                for (int j = i; j < slots.size(); j++) {
                    if (!slots.get(j).teachable()) {
                        break;
                    }
                    if (j > i && !slots.get(j).startMinute().equals(slots.get(j - 1).endMinute())) {
                        break;
                    }
                    covered.add(slots.get(j).id());
                    placements.add(new Placement(day, List.copyOf(covered),
                            slots.get(i).startMinute(), slots.get(j).endMinute()));
                }
            }
        }
        return placements;
    }

    /** Rough quality signal only; a fallback run is never presented as optimised. */
    private static double estimateScore(Map<Long, Integer> facultyLoad) {
        if (facultyLoad.size() < 2) {
            return 70.0;
        }
        int max = Collections.max(facultyLoad.values());
        int min = Collections.min(facultyLoad.values());
        double average = facultyLoad.values().stream().mapToInt(Integer::intValue).average().orElse(1);
        double balance = 1.0 - Math.min(1.0, (max - min) / Math.max(average, 1.0));
        return Math.round((55 + 25 * balance) * 10) / 10.0;
    }

    private static String format(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }
}
