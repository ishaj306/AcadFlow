package edu.batchmaker.service;

import edu.batchmaker.domain.entity.*;
import edu.batchmaker.domain.enums.*;
import java.time.LocalDate;
import edu.batchmaker.dto.conflict.ConflictResponse;
import edu.batchmaker.dto.timetable.ValidationSummary;
import edu.batchmaker.exception.ApiException;
import edu.batchmaker.repository.*;
import java.time.DayOfWeek;
import java.time.Duration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Independent verifier of a timetable (spec section 20).
 *
 * <p>Deliberately does not reuse the solver's own report: it re-reads the
 * persisted entries and re-checks every hard constraint against the current
 * database. If the optimizer were ever wrong, or the data changed after
 * generation, this is what catches it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConflictDetectionService {

    private final TimetableRepository timetableRepository;
    private final TimetableEntryRepository entryRepository;
    private final ConflictRepository conflictRepository;
    private final BatchStudentRepository batchStudentRepository;
    private final FacultyAvailabilityRepository facultyAvailabilityRepository;
    private final LabAvailabilityRepository labAvailabilityRepository;
    private final FacultySubjectRepository facultySubjectRepository;
    private final HolidayRepository holidayRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final LaboratoryRepository labRepository;

    /** A detected problem before it is persisted. */
    private record Finding(ConflictType type, Severity severity, String description, String resolution,
                           Long entryId, Long otherEntryId, TimetableEntry entry) {
    }

    @Transactional(readOnly = true)
    public List<ConflictResponse> list(ConflictStatus status) {
        List<Conflict> conflicts = status == null
                ? conflictRepository.findAll()
                : conflictRepository.findByStatusOrderBySeverityAscDetectedAtDesc(status);
        return conflicts.stream()
                .sorted(Comparator.comparing(Conflict::getSeverity).thenComparing(Conflict::getDetectedAt).reversed())
                .map(ConflictResponse::from)
                .toList();
    }

    /** Re-runs detection for a timetable and replaces its stored conflicts. */
    @Transactional
    public List<ConflictResponse> detectAndStore(Long timetableId) {
        Timetable timetable = timetableRepository.findById(timetableId)
                .orElseThrow(() -> ApiException.notFound("Timetable", timetableId));

        conflictRepository.deleteByTimetableId(timetableId);
        conflictRepository.flush();

        List<Finding> findings = analyse(timetableId);
        List<Conflict> saved = findings.stream().map(finding -> {
            Conflict conflict = new Conflict();
            conflict.setTimetable(timetable);
            conflict.setConflictType(finding.type());
            conflict.setSeverity(finding.severity());
            conflict.setDescription(finding.description());
            conflict.setSuggestedResolution(finding.resolution());
            conflict.setEntryId(finding.entryId());
            conflict.setOtherEntryId(finding.otherEntryId());
            conflict.setStatus(ConflictStatus.OPEN);
            TimetableEntry entry = finding.entry();
            if (entry != null) {
                conflict.setFaculty(entry.getFaculty());
                conflict.setBatch(entry.getBatch());
                conflict.setLab(entry.getLab());
                conflict.setSubject(entry.getSubject());
                conflict.setDayOfWeek(entry.getDayOfWeek());
                conflict.setStartTime(entry.getStartTime());
                conflict.setEndTime(entry.getEndTime());
            }
            return conflictRepository.save(conflict);
        }).toList();

        timetable.setHardViolations((int) findings.stream()
                .filter(f -> f.severity() == Severity.CRITICAL || f.severity() == Severity.HIGH)
                .count());

        return saved.stream().map(ConflictResponse::from).toList();
    }

    /** Validation summary for the preview screen. */
    @Transactional(readOnly = true)
    public ValidationSummary validate(Long timetableId) {
        List<Finding> findings = analyse(timetableId);
        List<TimetableEntry> entries = entryRepository.findDetailedByTimetableId(timetableId);

        Map<ConflictType, Long> counts = new EnumMap<>(ConflictType.class);
        findings.forEach(f -> counts.merge(f.type(), 1L, Long::sum));

        int hard = (int) findings.stream()
                .filter(f -> f.severity() == Severity.CRITICAL || f.severity() == Severity.HIGH)
                .count();

        List<String> warnings = findings.stream()
                .filter(f -> f.severity() == Severity.MEDIUM || f.severity() == Severity.LOW)
                .map(Finding::description)
                .distinct()
                .toList();

        return new ValidationSummary(
                hard == 0,
                hard,
                count(counts, ConflictType.FACULTY_DOUBLE_BOOKING),
                count(counts, ConflictType.LAB_DOUBLE_BOOKING),
                count(counts, ConflictType.STUDENT_BATCH_OVERLAP),
                count(counts, ConflictType.CAPACITY_VIOLATION),
                count(counts, ConflictType.FACULTY_UNAVAILABLE) + count(counts, ConflictType.LAB_UNAVAILABLE),
                count(counts, ConflictType.HOLIDAY_CONFLICT),
                count(counts, ConflictType.FACULTY_NOT_QUALIFIED) + count(counts, ConflictType.LAB_TYPE_MISMATCH),
                labUtilizationPercent(entries),
                workloadBalanceLabel(entries),
                count(counts, ConflictType.WORKLOAD_EXCEEDED),
                warnings);
    }

    // ------------------------------------------------------------------ core

    private List<Finding> analyse(Long timetableId) {
        List<TimetableEntry> entries = entryRepository.findDetailedByTimetableId(timetableId);
        List<Finding> findings = new ArrayList<>();
        if (entries.isEmpty()) {
            return findings;
        }

        findings.addAll(pairwiseConflicts(entries));
        findings.addAll(perEntryConflicts(entries));
        findings.addAll(holidayConflicts(entries));
        findings.addAll(duplicateAllocations(entries));
        findings.addAll(workloadConflicts(entries));
        return findings;
    }

    /** H1, H2 and H3: overlapping pairs on the same resource or student cohort. */
    private List<Finding> pairwiseConflicts(List<TimetableEntry> entries) {
        List<Finding> findings = new ArrayList<>();
        Map<Long, Set<Long>> rosters = new HashMap<>();

        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                TimetableEntry a = entries.get(i);
                TimetableEntry b = entries.get(j);
                if (!a.overlaps(b.getDayOfWeek(), b.getStartTime(), b.getEndTime())) {
                    continue;
                }
                String when = a.getDayOfWeek() + " " + a.getStartTime() + "-" + a.getEndTime();

                if (a.getFaculty().getId().equals(b.getFaculty().getId())) {
                    findings.add(new Finding(ConflictType.FACULTY_DOUBLE_BOOKING, Severity.CRITICAL,
                            a.getFaculty().getName() + " is assigned to " + a.getSubject().getSubjectName()
                                    + " (" + a.getBatch().getBatchName() + ") and "
                                    + b.getSubject().getSubjectName() + " (" + b.getBatch().getBatchName()
                                    + ") at the same time on " + when + ".",
                            "Move one session to a free slot, or assign a different qualified faculty member.",
                            a.getId(), b.getId(), a));
                }

                if (a.getLab().getId().equals(b.getLab().getId())) {
                    findings.add(new Finding(ConflictType.LAB_DOUBLE_BOOKING, Severity.CRITICAL,
                            a.getLab().getLabName() + " is booked for two practicals at " + when + ": "
                                    + a.getSubject().getSubjectName() + " and " + b.getSubject().getSubjectName() + ".",
                            "Move one session to another laboratory of the same type, or to a different slot.",
                            a.getId(), b.getId(), a));
                }

                Set<Long> rosterA = rosters.computeIfAbsent(a.getBatch().getId(), this::rosterOf);
                Set<Long> rosterB = rosters.computeIfAbsent(b.getBatch().getId(), this::rosterOf);
                if (!Collections.disjoint(rosterA, rosterB)) {
                    findings.add(new Finding(ConflictType.STUDENT_BATCH_OVERLAP, Severity.CRITICAL,
                            "Students in " + a.getBatch().getBatchName() + " are expected at both "
                                    + a.getSubject().getSubjectName() + " and " + b.getSubject().getSubjectName()
                                    + " on " + when + ".",
                            "Reschedule one of the two practicals for this cohort.",
                            a.getId(), b.getId(), a));
                }
            }
        }
        return findings;
    }

    /** Per-entry hard checks: capacity, availability, qualification, holidays. */
    private List<Finding> perEntryConflicts(List<TimetableEntry> entries) {
        List<Finding> findings = new ArrayList<>();

        Set<String> facultyBlocked = new HashSet<>();
        facultyAvailabilityRepository.findAllNonNeutral().forEach(row -> {
            if (row[3] == AvailabilityType.UNAVAILABLE) {
                facultyBlocked.add(row[0] + "|" + row[1] + "|" + row[2]);
            }
        });
        Set<String> labBlocked = new HashSet<>();
        labAvailabilityRepository.findAllNonNeutral().forEach(row -> {
            if (row[3] == AvailabilityType.UNAVAILABLE) {
                labBlocked.add(row[0] + "|" + row[1] + "|" + row[2]);
            }
        });

        Map<Long, Set<Long>> qualified = new HashMap<>();
        facultySubjectRepository.findAllQualificationPairs().forEach(row ->
                qualified.computeIfAbsent((Long) row[0], k -> new HashSet<>()).add((Long) row[1]));

        Map<Long, TimeSlot> slots = new HashMap<>();
        timeSlotRepository.findAll().forEach(s -> slots.put(s.getId(), s));

        for (TimetableEntry entry : entries) {
            int students = (int) batchStudentRepository.countByBatchId(entry.getBatch().getId());

            if (students > entry.getLab().getCapacity()) {
                findings.add(new Finding(ConflictType.CAPACITY_VIOLATION, Severity.CRITICAL,
                        entry.getBatch().getBatchName() + " has " + students + " students but "
                                + entry.getLab().getLabName() + " seats only " + entry.getLab().getCapacity() + ".",
                        "Move the batch to a larger laboratory or split it further.",
                        entry.getId(), null, entry));
            }

            if (!entry.getLab().getLabType().equals(entry.getBatch().getRequiredLabType())) {
                findings.add(new Finding(ConflictType.LAB_TYPE_MISMATCH, Severity.HIGH,
                        entry.getSubject().getSubjectName() + " requires a '"
                                + entry.getBatch().getRequiredLabType() + "' laboratory but is scheduled in "
                                + entry.getLab().getLabName() + " (" + entry.getLab().getLabType() + ").",
                        "Reassign the session to a laboratory of the required type.",
                        entry.getId(), null, entry));
            }

            if (entry.getLab().getStatus() != LabStatus.ACTIVE) {
                findings.add(new Finding(ConflictType.LAB_UNAVAILABLE, Severity.HIGH,
                        entry.getLab().getLabName() + " is marked "
                                + entry.getLab().getStatus().name().toLowerCase()
                                + " but still hosts " + entry.getSubject().getSubjectName() + ".",
                        "Reschedule the session to an active laboratory.",
                        entry.getId(), null, entry));
            }

            Set<Long> subjects = qualified.getOrDefault(entry.getFaculty().getId(), Set.of());
            if (!subjects.contains(entry.getSubject().getId())) {
                findings.add(new Finding(ConflictType.FACULTY_NOT_QUALIFIED, Severity.HIGH,
                        entry.getFaculty().getName() + " is not listed as qualified to teach "
                                + entry.getSubject().getSubjectName() + ".",
                        "Assign a qualified faculty member, or add the subject to their qualifications.",
                        entry.getId(), null, entry));
            }

            for (Long slotId : occupiedSlotIds(entry, slots)) {
                if (facultyBlocked.contains(entry.getFaculty().getId() + "|" + entry.getDayOfWeek() + "|" + slotId)) {
                    findings.add(new Finding(ConflictType.FACULTY_UNAVAILABLE, Severity.HIGH,
                            entry.getFaculty().getName() + " is marked unavailable on "
                                    + entry.getDayOfWeek() + " at " + entry.getStartTime() + ".",
                            "Move the session into one of their available slots.",
                            entry.getId(), null, entry));
                    break;
                }
            }
            for (Long slotId : occupiedSlotIds(entry, slots)) {
                if (labBlocked.contains(entry.getLab().getId() + "|" + entry.getDayOfWeek() + "|" + slotId)) {
                    findings.add(new Finding(ConflictType.LAB_UNAVAILABLE, Severity.HIGH,
                            entry.getLab().getLabName() + " is unavailable on "
                                    + entry.getDayOfWeek() + " at " + entry.getStartTime() + ".",
                            "Move the session to another laboratory or slot.",
                            entry.getId(), null, entry));
                    break;
                }
            }

            TimeSlot startSlot = slots.get(entry.getStartTimeSlot().getId());
            if (startSlot != null && !startSlot.isSchedulable()) {
                findings.add(new Finding(ConflictType.NON_TEACHING_SLOT, Severity.HIGH,
                        entry.getSubject().getSubjectName() + " is scheduled in the '"
                                + startSlot.getLabel() + "' period, which is not a teaching slot.",
                        "Move the session into a teaching period.",
                        entry.getId(), null, entry));
            }

        }
        return findings;
    }

    /**
     * One finding per holiday, not per affected session.
     *
     * <p>A holiday cancels a single calendar date, so raising a conflict against
     * every practical that merely shares its weekday buries the genuine hard
     * violations under dozens of low-value rows.
     */
    private List<Finding> holidayConflicts(List<TimetableEntry> entries) {
        Map<DayOfWeek, List<TimetableEntry>> byDay = new EnumMap<>(DayOfWeek.class);
        entries.forEach(e -> byDay.computeIfAbsent(e.getDayOfWeek(), k -> new ArrayList<>()).add(e));

        List<Finding> findings = new ArrayList<>();
        for (Holiday holiday : holidayRepository.findAllByOrderByHolidayDateAsc()) {
            List<TimetableEntry> affected = byDay.get(holiday.getHolidayDate().getDayOfWeek());
            if (affected == null || affected.isEmpty()) {
                continue;
            }
            Finding finding = new Finding(ConflictType.HOLIDAY_CONFLICT, Severity.LOW,
                    holiday.getName() + " on " + holiday.getHolidayDate() + " is a "
                            + holiday.getHolidayDate().getDayOfWeek() + ", so " + affected.size()
                            + " practical session(s) scheduled that weekday will not run on that date.",
                    "Reschedule the affected sessions for that date from the Rescheduling screen, "
                            + "or treat them as cancelled for the week.",
                    null, null, null);
            // Anchor the conflict to the date so the UI can show it on a calendar.
            findings.add(finding);
        }
        return findings;
    }

    /** A practical scheduled more often than its subject requires. */
    private List<Finding> duplicateAllocations(List<TimetableEntry> entries) {
        Map<String, List<TimetableEntry>> byPractical = new LinkedHashMap<>();
        for (TimetableEntry entry : entries) {
            String key = entry.getBatch().getId() + "|" + entry.getSubject().getId();
            byPractical.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
        }

        List<Finding> findings = new ArrayList<>();
        byPractical.forEach((key, group) -> {
            TimetableEntry first = group.get(0);
            int expected = first.getSubject().getSessionsPerWeek();
            if (group.size() > expected) {
                findings.add(new Finding(ConflictType.DUPLICATE_ALLOCATION, Severity.MEDIUM,
                        first.getBatch().getBatchName() + " has " + group.size() + " sessions of "
                                + first.getSubject().getSubjectName() + " but the subject requires "
                                + expected + " per week.",
                        "Remove the surplus session(s).",
                        first.getId(), null, first));
            }
        });
        return findings;
    }

    /** Weekly hour limits (spec section 26). */
    private List<Finding> workloadConflicts(List<TimetableEntry> entries) {
        Map<Long, Integer> minutes = new HashMap<>();
        Map<Long, Faculty> faculty = new HashMap<>();
        for (TimetableEntry entry : entries) {
            faculty.put(entry.getFaculty().getId(), entry.getFaculty());
            minutes.merge(entry.getFaculty().getId(), durationMinutes(entry), Integer::sum);
        }

        List<Finding> findings = new ArrayList<>();
        minutes.forEach((facultyId, assigned) -> {
            Faculty member = faculty.get(facultyId);
            int limit = member.getMaxWeeklyHours() * 60;
            if (assigned > limit) {
                findings.add(new Finding(ConflictType.WORKLOAD_EXCEEDED, Severity.MEDIUM,
                        member.getName() + " is assigned " + format(assigned)
                                + " per week against a limit of " + member.getMaxWeeklyHours() + "h.",
                        "Move a practical to a colleague who is under their limit.",
                        null, null, null));
            }
        });
        return findings;
    }

    // ------------------------------------------------------------- utilities

    private Set<Long> rosterOf(Long batchId) {
        Set<Long> students = new HashSet<>();
        batchStudentRepository.findByBatchIdWithStudent(batchId)
                .forEach(bs -> students.add(bs.getStudent().getId()));
        return students;
    }

    /** Slot ids a session covers, derived from its start/end period ordering. */
    private List<Long> occupiedSlotIds(TimetableEntry entry, Map<Long, TimeSlot> slots) {
        TimeSlot start = slots.get(entry.getStartTimeSlot().getId());
        TimeSlot end = slots.get(entry.getEndTimeSlot().getId());
        if (start == null || end == null) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        slots.values().stream()
                .filter(s -> s.getSlotOrder() >= start.getSlotOrder() && s.getSlotOrder() <= end.getSlotOrder())
                .sorted(Comparator.comparing(TimeSlot::getSlotOrder))
                .forEach(s -> ids.add(s.getId()));
        return ids;
    }

    private static int durationMinutes(TimetableEntry entry) {
        return (int) Duration.between(entry.getStartTime(), entry.getEndTime()).toMinutes();
    }

    private static int count(Map<ConflictType, Long> counts, ConflictType type) {
        return counts.getOrDefault(type, 0L).intValue();
    }

    private static String format(int minutes) {
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    /**
     * Share of available laboratory periods that are actually used, across all
     * active labs and the days/slots the schedule spans.
     */
    private double labUtilizationPercent(List<TimetableEntry> entries) {
        if (entries.isEmpty()) {
            return 0.0;
        }
        long activeLabs = labRepository.countByStatus(LabStatus.ACTIVE);
        if (activeLabs == 0) {
            return 0.0;
        }
        long teachingSlots = timeSlotRepository.findSchedulableSlots().size();
        long days = entries.stream().map(TimetableEntry::getDayOfWeek).distinct().count();
        long capacityMinutes = activeLabs * teachingSlots * days * 60;
        if (capacityMinutes == 0) {
            return 0.0;
        }
        long usedMinutes = entries.stream().mapToLong(ConflictDetectionService::durationMinutes).sum();
        return Math.round(usedMinutes * 1000.0 / capacityMinutes) / 10.0;
    }

    private String workloadBalanceLabel(List<TimetableEntry> entries) {
        if (entries.isEmpty()) {
            return "No data";
        }
        Map<Long, Integer> minutes = new HashMap<>();
        entries.forEach(e -> minutes.merge(e.getFaculty().getId(), durationMinutes(e), Integer::sum));
        if (minutes.size() < 2) {
            return "Single assignee";
        }
        int max = Collections.max(minutes.values());
        int min = Collections.min(minutes.values());
        int spread = max - min;
        if (spread <= 120) {
            return "Good";
        }
        if (spread <= 240) {
            return "Acceptable";
        }
        return "Uneven";
    }

    @Transactional
    public ConflictResponse updateStatus(Long conflictId, ConflictStatus status) {
        Conflict conflict = conflictRepository.findById(conflictId)
                .orElseThrow(() -> ApiException.notFound("Conflict", conflictId));
        conflict.setStatus(status);
        if (status == ConflictStatus.RESOLVED) {
            conflict.setResolvedAt(java.time.Instant.now());
        }
        return ConflictResponse.from(conflict);
    }
}
