package edu.batchmaker.service.solver;

import edu.batchmaker.domain.entity.*;
import edu.batchmaker.domain.enums.AvailabilityType;
import edu.batchmaker.domain.enums.LabStatus;
import edu.batchmaker.domain.enums.RecordStatus;
import edu.batchmaker.dto.solver.SolverPayload;
import edu.batchmaker.repository.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns the database into the self-contained snapshot the CP-SAT service needs.
 *
 * <p>Hard constraints are encoded here as much as possible by *omission*:
 * unavailable cells, inactive labs and unqualified faculty simply never reach
 * the solver, so no candidate placement violating them can be generated.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleDataAssembler {

    private final TimeSlotRepository timeSlotRepository;
    private final WorkingDayRepository workingDayRepository;
    private final LaboratoryRepository labRepository;
    private final FacultyRepository facultyRepository;
    private final FacultySubjectRepository facultySubjectRepository;
    private final FacultyAvailabilityRepository facultyAvailabilityRepository;
    private final LabAvailabilityRepository labAvailabilityRepository;
    private final FacultyLeaveRepository leaveRepository;
    private final PracticalRepository practicalRepository;
    private final BatchStudentRepository batchStudentRepository;
    private final OptimizationWeightRepository weightRepository;
    private final FixedCommitmentRepository fixedCommitmentRepository;

    /** Everything the solver needs, plus the lookups the caller needs afterwards. */
    public record Snapshot(SolverPayload.Request request,
                           List<TimeSlot> slots,
                           List<Practical> practicals,
                           Map<Long, Practical> practicalsById,
                           Map<Long, TimeSlot> slotsById,
                           Map<Long, Faculty> facultyById,
                           Map<Long, Laboratory> labsById) {
    }

    @Transactional(readOnly = true)
    public Snapshot assemble(AcademicTerm term, double maxSeconds,
                             List<SolverPayload.PreviousAssignment> previous,
                             List<SolverPayload.PreviousAssignment> locked) {

        List<DayOfWeek> days = workingDayRepository.findByActiveTrueOrderByDayOrderAsc().stream()
                .map(WorkingDay::getDayOfWeek).toList();
        List<TimeSlot> slots = timeSlotRepository.findAllByOrderBySlotOrderAsc().stream()
                .filter(TimeSlot::isActive).toList();

        List<Laboratory> labs = labRepository.findByStatusOrderByLabNameAsc(LabStatus.ACTIVE);
        List<Faculty> faculty = facultyRepository.findByStatusOrderByNameAsc(RecordStatus.ACTIVE);
        List<Practical> practicals = practicalRepository.findActiveForTerm(term.getId());

        Map<Long, List<Long>> qualifications = qualificationsByFaculty();
        Map<Long, Map<AvailabilityType, List<List<Object>>>> facultyCells = facultyAvailabilityCells();
        Map<Long, List<List<Object>>> labCells = labUnavailableCells();
        Map<Long, Set<DayOfWeek>> leaveBlockedDays = fullTermLeaveDays(term, days);

        // Fixed commitments (lectures/meetings/reserved labs) block their cells
        // for the named faculty and/or lab, and add to faculty base load.
        FixedCommitments fixed = fixedCommitments(term, slots);

        List<SolverPayload.FacultyIn> facultyIn = faculty.stream().map(f -> {
            var cells = facultyCells.getOrDefault(f.getId(), Map.of());
            List<List<Object>> unavailable = new ArrayList<>(
                    cells.getOrDefault(AvailabilityType.UNAVAILABLE, List.of()));
            // A leave covering every occurrence of a weekday blocks that weekday
            // outright; shorter leaves are handled by the rescheduling workflow.
            for (DayOfWeek day : leaveBlockedDays.getOrDefault(f.getId(), Set.of())) {
                for (TimeSlot slot : slots) {
                    unavailable.add(List.of(day.name(), slot.getId()));
                }
            }
            unavailable.addAll(fixed.facultyCells().getOrDefault(f.getId(), List.of()));
            return new SolverPayload.FacultyIn(
                    f.getId(),
                    f.getMaxWeeklyHours() * 60,
                    qualifications.getOrDefault(f.getId(), List.of()),
                    unavailable,
                    cells.getOrDefault(AvailabilityType.PREFERRED, List.of()),
                    fixed.facultyLoadMinutes().getOrDefault(f.getId(), 0));
        }).toList();

        List<SolverPayload.LabIn> labsIn = labs.stream()
                .map(l -> {
                    List<List<Object>> unavailable =
                            new ArrayList<>(labCells.getOrDefault(l.getId(), List.of()));
                    unavailable.addAll(fixed.labCells().getOrDefault(l.getId(), List.of()));
                    return new SolverPayload.LabIn(l.getId(), l.getCapacity(), l.getLabType(), unavailable);
                })
                .toList();

        Map<Long, Integer> batchSizes = new HashMap<>();
        List<SolverPayload.PracticalIn> practicalsIn = practicals.stream().map(p -> {
            int size = (int) batchStudentRepository.countByBatchId(p.getBatch().getId());
            batchSizes.put(p.getBatch().getId(), size);
            return new SolverPayload.PracticalIn(
                    p.getId(),
                    p.getSubject().getId(),
                    p.getBatch().getId(),
                    size,
                    p.getBatch().getRequiredLabType(),
                    p.getDurationMin(),
                    p.getSessionsPerWeek(),
                    p.getPreferredFaculty() == null ? null : p.getPreferredFaculty().getId());
        }).toList();

        StudentGrouping grouping = studentGrouping(practicals);
        log.debug("Solver input: {} conflict groups, {} cohorts",
                grouping.conflictGroups().size(), grouping.cohorts().size());

        SolverPayload.Request request = new SolverPayload.Request(
                days.stream().map(DayOfWeek::name).toList(),
                slots.stream()
                        .map(s -> new SolverPayload.TimeSlotIn(s.getId(), s.getSlotOrder(),
                                minutes(s.getStartTime()), minutes(s.getEndTime()),
                                s.getSlotType().isTeachable()))
                        .toList(),
                labsIn,
                facultyIn,
                practicalsIn,
                grouping.conflictGroups(),
                grouping.cohorts(),
                locked == null ? List.of() : locked,
                previous == null ? List.of() : previous,
                weights(),
                maxSeconds,
                8);

        return new Snapshot(
                request,
                slots,
                practicals,
                practicals.stream().collect(HashMap::new, (m, p) -> m.put(p.getId(), p), HashMap::putAll),
                slots.stream().collect(HashMap::new, (m, s) -> m.put(s.getId(), s), HashMap::putAll),
                faculty.stream().collect(HashMap::new, (m, f) -> m.put(f.getId(), f), HashMap::putAll),
                labs.stream().collect(HashMap::new, (m, l) -> m.put(l.getId(), l), HashMap::putAll));
    }

    /** Conflict groups (hard constraint H3) and cohorts (soft constraint S3). */
    public record StudentGrouping(List<List<Long>> conflictGroups, List<List<Long>> cohorts) {
    }

    /**
     * Practicals that share at least one student may never overlap (H3).
     *
     * <p>Batches with an identical roster - the usual case, since batch A of
     * every subject holds the same students - collapse into one group. Any
     * remaining partial overlap between different rosters is emitted as an
     * explicit pair, which keeps the constraint exact.
     *
     * <p>Those pairs are returned separately from the cohorts, because feeding
     * the full pair list into the student-gap objective would generate
     * thousands of near-duplicate variables without improving accuracy.
     */
    public StudentGrouping studentGrouping(List<Practical> practicals) {
        Map<Long, Set<Long>> rosters = new LinkedHashMap<>();
        for (Practical practical : practicals) {
            Set<Long> students = new HashSet<>();
            batchStudentRepository.findByBatchIdWithStudent(practical.getBatch().getId())
                    .forEach(bs -> students.add(bs.getStudent().getId()));
            rosters.put(practical.getId(), students);
        }

        // Collapse identical rosters into cohorts.
        Map<Set<Long>, List<Long>> byRoster = new LinkedHashMap<>();
        rosters.forEach((practicalId, roster) ->
                byRoster.computeIfAbsent(roster, key -> new ArrayList<>()).add(practicalId));

        List<List<Long>> cohorts = new ArrayList<>();
        List<List<Long>> conflictGroups = new ArrayList<>();
        byRoster.forEach((roster, members) -> {
            cohorts.add(members);
            if (members.size() > 1) {
                conflictGroups.add(members);
            }
        });

        // Partial overlaps between different rosters become pairwise groups.
        List<Map.Entry<Set<Long>, List<Long>>> entries = new ArrayList<>(byRoster.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                if (Collections.disjoint(entries.get(i).getKey(), entries.get(j).getKey())) {
                    continue;
                }
                for (Long a : entries.get(i).getValue()) {
                    for (Long b : entries.get(j).getValue()) {
                        conflictGroups.add(List.of(a, b));
                    }
                }
            }
        }
        return new StudentGrouping(conflictGroups, cohorts);
    }

    private Map<Long, List<Long>> qualificationsByFaculty() {
        Map<Long, List<Long>> result = new HashMap<>();
        for (Object[] row : facultySubjectRepository.findAllQualificationPairs()) {
            result.computeIfAbsent((Long) row[0], key -> new ArrayList<>()).add((Long) row[1]);
        }
        return result;
    }

    private Map<Long, Map<AvailabilityType, List<List<Object>>>> facultyAvailabilityCells() {
        Map<Long, Map<AvailabilityType, List<List<Object>>>> result = new HashMap<>();
        for (Object[] row : facultyAvailabilityRepository.findAllNonNeutral()) {
            Long facultyId = (Long) row[0];
            DayOfWeek day = (DayOfWeek) row[1];
            Long slotId = (Long) row[2];
            AvailabilityType type = (AvailabilityType) row[3];
            result.computeIfAbsent(facultyId, key -> new EnumMap<>(AvailabilityType.class))
                    .computeIfAbsent(type, key -> new ArrayList<>())
                    .add(List.of(day.name(), slotId));
        }
        return result;
    }

    private Map<Long, List<List<Object>>> labUnavailableCells() {
        Map<Long, List<List<Object>>> result = new HashMap<>();
        for (Object[] row : labAvailabilityRepository.findAllNonNeutral()) {
            AvailabilityType type = (AvailabilityType) row[3];
            if (!type.blocksAssignment()) {
                continue;
            }
            Long labId = (Long) row[0];
            DayOfWeek day = (DayOfWeek) row[1];
            Long slotId = (Long) row[2];
            result.computeIfAbsent(labId, key -> new ArrayList<>()).add(List.of(day.name(), slotId));
        }
        return result;
    }

    /**
     * Weekdays on which a faculty member is on approved leave for *every*
     * occurrence inside the term. Only these can safely block a weekly slot.
     */
    private Map<Long, Set<DayOfWeek>> fullTermLeaveDays(AcademicTerm term, List<DayOfWeek> days) {
        List<FacultyLeave> leaves = leaveRepository.findApprovedOverlapping(
                term.getStartDate(), term.getEndDate());
        if (leaves.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<FacultyLeave>> byFaculty = new HashMap<>();
        leaves.forEach(l -> byFaculty.computeIfAbsent(l.getFaculty().getId(), k -> new ArrayList<>()).add(l));

        Map<Long, Set<DayOfWeek>> result = new HashMap<>();
        byFaculty.forEach((facultyId, facultyLeaves) -> {
            for (DayOfWeek day : days) {
                boolean allCovered = true;
                boolean any = false;
                for (LocalDate date = term.getStartDate();
                     !date.isAfter(term.getEndDate());
                     date = date.plusDays(1)) {
                    if (date.getDayOfWeek() != day) {
                        continue;
                    }
                    any = true;
                    LocalDate probe = date;
                    if (facultyLeaves.stream().noneMatch(l -> l.covers(probe))) {
                        allCovered = false;
                        break;
                    }
                }
                if (any && allCovered) {
                    result.computeIfAbsent(facultyId, k -> EnumSet.noneOf(DayOfWeek.class)).add(day);
                }
            }
        });
        return result;
    }

    /** Blocked cells (per faculty, per lab) and base load (per faculty) from fixed commitments. */
    private record FixedCommitments(Map<Long, List<List<Object>>> facultyCells,
                                    Map<Long, List<List<Object>>> labCells,
                                    Map<Long, Integer> facultyLoadMinutes) {
    }

    /**
     * Expands each fixed commitment in force for the term into blocked (day, slot)
     * cells for its faculty member and/or laboratory, and totals the minutes each
     * faculty member spends on commitments so the workload objective can add them
     * to the practical load.
     */
    private FixedCommitments fixedCommitments(AcademicTerm term, List<TimeSlot> slots) {
        Map<Long, List<List<Object>>> facultyCells = new HashMap<>();
        Map<Long, List<List<Object>>> labCells = new HashMap<>();
        Map<Long, Integer> facultyLoad = new HashMap<>();

        for (FixedCommitment commitment : fixedCommitmentRepository.findForTerm(term.getId())) {
            int startOrder = commitment.getStartTimeSlot().getSlotOrder();
            int endOrder = commitment.getEndTimeSlot().getSlotOrder();
            String day = commitment.getDayOfWeek().name();

            List<TimeSlot> covered = slots.stream()
                    .filter(s -> s.getSlotOrder() >= startOrder && s.getSlotOrder() <= endOrder)
                    .toList();

            if (commitment.getFaculty() != null) {
                Long facultyId = commitment.getFaculty().getId();
                List<List<Object>> cells = facultyCells.computeIfAbsent(facultyId, k -> new ArrayList<>());
                covered.forEach(s -> cells.add(List.of(day, s.getId())));
            }
            if (commitment.getLab() != null) {
                Long labId = commitment.getLab().getId();
                List<List<Object>> cells = labCells.computeIfAbsent(labId, k -> new ArrayList<>());
                covered.forEach(s -> cells.add(List.of(day, s.getId())));
            }
            if (commitment.getFaculty() != null) {
                int minutes = (int) java.time.Duration.between(
                        commitment.getStartTimeSlot().getStartTime(),
                        commitment.getEndTimeSlot().getEndTime()).toMinutes();
                facultyLoad.merge(commitment.getFaculty().getId(), minutes, Integer::sum);
            }
        }
        return new FixedCommitments(facultyCells, labCells, facultyLoad);
    }

    /** Objective weights, overridable per installation from the database. */
    public SolverPayload.Weights weights() {
        return new SolverPayload.Weights(
                weight("workload_imbalance", 6.0),
                weight("faculty_preference", 2.0),
                weight("student_gaps", 3.0),
                weight("lab_underutilization", 1.0),
                weight("schedule_changes", 10.0),
                weight("faculty_idle", 2.0));
    }

    private double weight(String key, double fallback) {
        return weightRepository.findByWeightKey(key)
                .map(OptimizationWeight::getWeightValue)
                .orElse(fallback);
    }

    private static int minutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }
}
