package edu.batchmaker.service;

import edu.batchmaker.domain.entity.*;
import edu.batchmaker.domain.enums.*;
import edu.batchmaker.dto.reschedule.*;
import edu.batchmaker.exception.ApiException;
import edu.batchmaker.exception.ErrorCode;
import edu.batchmaker.repository.*;
import edu.batchmaker.security.CurrentUser;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auto-rescheduling engine (spec sections 21-24).
 *
 * <p>When a practical is disrupted, this searches every legal alternative
 * placement against the *current* published timetable, scores each one, and
 * presents a ranked shortlist. Nothing moves until a coordinator approves a
 * candidate, and every decision is recorded for audit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReschedulingService {

    private static final int DEFAULT_CANDIDATES = 5;

    private final RescheduleRepository rescheduleRepository;
    private final RescheduleCandidateRepository candidateRepository;
    private final TimetableEntryRepository entryRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final WorkingDayRepository workingDayRepository;
    private final LaboratoryRepository labRepository;
    private final FacultyRepository facultyRepository;
    private final FacultySubjectRepository facultySubjectRepository;
    private final FacultyAvailabilityRepository facultyAvailabilityRepository;
    private final LabAvailabilityRepository labAvailabilityRepository;
    private final FacultyLeaveRepository leaveRepository;
    private final BatchStudentRepository batchStudentRepository;
    private final HolidayRepository holidayRepository;
    private final UserRepository userRepository;

    private final NotificationService notificationService;
    private final ConflictDetectionService conflictService;
    private final AuditService auditService;
    private final CurrentUser currentUser;

    /** A legal alternative placement, before it is persisted. */
    private record Option(DayOfWeek day, List<TimeSlot> slots, LocalTime start, LocalTime end,
                          Faculty faculty, Laboratory lab, double score, String breakdown) {
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public List<RescheduleResponse> list(RescheduleStatus status) {
        var reschedules = status == null
                ? rescheduleRepository.findAllByOrderByCreatedAtDesc()
                : rescheduleRepository.findByStatusOrderByCreatedAtDesc(status);
        return reschedules.stream().map(r -> RescheduleResponse.from(r, List.of())).toList();
    }

    @Transactional(readOnly = true)
    public RescheduleResponse get(Long id) {
        Reschedule reschedule = rescheduleRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Reschedule request", id));
        return RescheduleResponse.from(reschedule, candidatesOf(id));
    }

    private List<RescheduleCandidateResponse> candidatesOf(Long rescheduleId) {
        return candidateRepository.findDetailedByRescheduleId(rescheduleId).stream()
                .map(RescheduleCandidateResponse::from).toList();
    }

    // --------------------------------------------------------------- analysis

    /**
     * Finds and ranks alternatives for one disrupted session, and records the
     * proposal. This does not change the timetable.
     */
    @Transactional
    public RescheduleResponse analyze(RescheduleAnalyzeRequest request) {
        TimetableEntry entry = entryRepository.findDetailedById(request.timetableEntryId())
                .orElseThrow(() -> ApiException.notFound("Timetable entry", request.timetableEntryId()));

        int limit = request.maxCandidates() == null ? DEFAULT_CANDIDATES : Math.max(1, request.maxCandidates());
        List<Option> options = findOptions(entry, request.affectedDate());

        if (options.isEmpty()) {
            throw new ApiException(ErrorCode.NO_ALTERNATIVE_SLOT,
                    "No alternative slot satisfies every constraint for "
                            + entry.getSubject().getSubjectName() + " (" + entry.getBatch().getBatchName() + "). "
                            + "Free a laboratory, widen faculty availability, or cancel the session instead.");
        }

        Reschedule reschedule = new Reschedule();
        reschedule.setTimetableEntry(entry);
        reschedule.setTimetable(entry.getTimetable());
        reschedule.setReason(request.reason());
        reschedule.setReasonDetail(request.reasonDetail());
        reschedule.setAffectedDate(request.affectedDate());
        reschedule.setOriginalDay(entry.getDayOfWeek());
        reschedule.setOriginalStart(entry.getStartTime());
        reschedule.setOriginalEnd(entry.getEndTime());
        reschedule.setOriginalFaculty(entry.getFaculty());
        reschedule.setOriginalLab(entry.getLab());
        reschedule.setStatus(RescheduleStatus.PROPOSED);
        reschedule.setCreatedAt(Instant.now());
        currentUser.userId().flatMap(userRepository::findById).ifPresent(reschedule::setInitiatedBy);
        Reschedule saved = rescheduleRepository.save(reschedule);

        int rank = 1;
        for (Option option : options.stream().limit(limit).toList()) {
            RescheduleCandidate candidate = new RescheduleCandidate();
            candidate.setReschedule(saved);
            candidate.setRankOrder(rank++);
            candidate.setDayOfWeek(option.day());
            candidate.setStartTimeSlot(option.slots().get(0));
            candidate.setEndTimeSlot(option.slots().get(option.slots().size() - 1));
            candidate.setStartTime(option.start());
            candidate.setEndTime(option.end());
            candidate.setFaculty(option.faculty());
            candidate.setLab(option.lab());
            candidate.setScore(option.score());
            candidate.setScoreBreakdown(option.breakdown());
            candidateRepository.save(candidate);
        }

        // The top-ranked option becomes the standing recommendation.
        Option best = options.get(0);
        saved.setCandidateScore(best.score());

        auditService.record("ANALYZE_RESCHEDULE", "Reschedule", saved.getId(), null,
                entry.getSubject().getSubjectCode() + " " + entry.getBatch().getBatchName()
                        + " reason=" + request.reason() + " options=" + options.size());

        return RescheduleResponse.from(saved, candidatesOf(saved.getId()));
    }

    /**
     * Raises a reschedule proposal for every published practical an approved
     * leave collides with (spec demo steps 11-13).
     */
    @Transactional
    public List<RescheduleResponse> analyzeForLeave(Long leaveId) {
        FacultyLeave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> ApiException.notFound("Leave request", leaveId));

        if (leave.getStatus() != LeaveStatus.APPROVED) {
            throw new ApiException(ErrorCode.RESCHEDULE_STATE_INVALID,
                    "Only approved leave triggers rescheduling. This request is "
                            + leave.getStatus().name().toLowerCase() + ".");
        }

        // Every weekday the leave actually covers.
        Set<DayOfWeek> affectedDays = new LinkedHashSet<>();
        Map<DayOfWeek, LocalDate> firstDate = new EnumMap<>(DayOfWeek.class);
        for (LocalDate date = leave.getStartDate(); !date.isAfter(leave.getEndDate()); date = date.plusDays(1)) {
            affectedDays.add(date.getDayOfWeek());
            firstDate.putIfAbsent(date.getDayOfWeek(), date);
        }

        List<TimetableEntry> affected = entryRepository.findAll().stream()
                .filter(e -> e.getStatus() != EntryStatus.CANCELLED)
                .filter(e -> e.getTimetable().getStatus() == TimetableStatus.PUBLISHED)
                .filter(e -> e.getFaculty().getId().equals(leave.getFaculty().getId()))
                .filter(e -> affectedDays.contains(e.getDayOfWeek()))
                .toList();

        if (affected.isEmpty()) {
            return List.of();
        }

        List<RescheduleResponse> proposals = new ArrayList<>();
        for (TimetableEntry entry : affected) {
            // Skip anything already awaiting a decision.
            boolean pending = rescheduleRepository.findByTimetableEntryIdOrderByCreatedAtDesc(entry.getId())
                    .stream().anyMatch(r -> r.getStatus() == RescheduleStatus.PROPOSED);
            if (pending) {
                continue;
            }
            try {
                proposals.add(analyze(new RescheduleAnalyzeRequest(
                        entry.getId(),
                        RescheduleReason.FACULTY_LEAVE,
                        leave.getFaculty().getName() + " is on approved "
                                + leave.getLeaveType().toLowerCase() + " leave from "
                                + leave.getStartDate() + " to " + leave.getEndDate() + ".",
                        firstDate.get(entry.getDayOfWeek()),
                        DEFAULT_CANDIDATES)));
            } catch (ApiException ex) {
                log.warn("No alternative found for entry {}: {}", entry.getId(), ex.getMessage());
            }
        }
        return proposals;
    }

    // --------------------------------------------------------------- approval

    @Transactional
    public RescheduleResponse approve(Long rescheduleId, RescheduleApproveRequest request) {
        Reschedule reschedule = rescheduleRepository.findById(rescheduleId)
                .orElseThrow(() -> ApiException.notFound("Reschedule request", rescheduleId));

        if (reschedule.getStatus() != RescheduleStatus.PROPOSED) {
            throw new ApiException(ErrorCode.RESCHEDULE_STATE_INVALID,
                    "This request has already been " + reschedule.getStatus().name().toLowerCase() + ".");
        }

        TimetableEntry entry = entryRepository.findDetailedById(reschedule.getTimetableEntry().getId())
                .orElseThrow(() -> ApiException.notFound("Timetable entry",
                        reschedule.getTimetableEntry().getId()));

        DayOfWeek newDay;
        TimeSlot startSlot;
        TimeSlot endSlot;
        Faculty newFaculty;
        Laboratory newLab;
        Double score;

        if (request.isManual()) {
            newDay = require(request.dayOfWeek(), "day");
            startSlot = timeSlotRepository.findById(require(request.startSlotId(), "start slot"))
                    .orElseThrow(() -> ApiException.notFound("Time slot", request.startSlotId()));
            newFaculty = facultyRepository.findById(require(request.facultyId(), "faculty"))
                    .orElseThrow(() -> ApiException.notFound("Faculty", request.facultyId()));
            newLab = labRepository.findById(require(request.labId(), "laboratory"))
                    .orElseThrow(() -> ApiException.notFound("Laboratory", request.labId()));

            List<TimeSlot> run = contiguousRun(startSlot, durationMinutes(entry));
            if (run.isEmpty()) {
                throw ApiException.validation(
                        "The chosen period cannot hold a " + durationMinutes(entry) + "-minute practical.");
            }
            endSlot = run.get(run.size() - 1);

            // A manual choice is still checked against every hard constraint.
            String rejection = firstViolation(entry, newDay, run, newFaculty, newLab,
                    reschedule.getAffectedDate());
            if (rejection != null) {
                throw new ApiException(ErrorCode.TIMETABLE_CONFLICT, rejection);
            }
            score = null;
        } else {
            RescheduleCandidate candidate = candidateRepository.findById(request.candidateId())
                    .orElseThrow(() -> ApiException.notFound("Candidate", request.candidateId()));
            if (!candidate.getReschedule().getId().equals(rescheduleId)) {
                throw ApiException.validation("That candidate belongs to a different reschedule request.");
            }
            newDay = candidate.getDayOfWeek();
            startSlot = candidate.getStartTimeSlot();
            endSlot = candidate.getEndTimeSlot();
            newFaculty = candidate.getFaculty();
            newLab = candidate.getLab();
            score = candidate.getScore();
            candidate.setSelected(true);
        }

        DayOfWeek oldDay = entry.getDayOfWeek();
        LocalTime oldStart = entry.getStartTime();
        String oldFacultyName = entry.getFaculty().getName();
        String oldLabName = entry.getLab().getLabName();

        LocalTime newStart = startSlot.getStartTime();
        LocalTime newEnd = newStart.plusMinutes(durationMinutes(entry));

        entry.setDayOfWeek(newDay);
        entry.setStartTimeSlot(startSlot);
        entry.setEndTimeSlot(endSlot);
        entry.setStartTime(newStart);
        entry.setEndTime(newEnd);
        entry.setFaculty(newFaculty);
        entry.setLab(newLab);
        entry.setStatus(EntryStatus.RESCHEDULED);

        Instant now = Instant.now();
        reschedule.setNewDay(newDay);
        reschedule.setNewStart(newStart);
        reschedule.setNewEnd(newEnd);
        reschedule.setNewFaculty(newFaculty);
        reschedule.setNewLab(newLab);
        reschedule.setCandidateScore(score);
        reschedule.setStatus(RescheduleStatus.APPLIED);
        reschedule.setApprovedAt(now);
        reschedule.setAppliedAt(now);
        currentUser.userId().flatMap(userRepository::findById).ifPresent(reschedule::setApprovedBy);

        entryRepository.flush();
        conflictService.detectAndStore(entry.getTimetable().getId());

        notificationService.notifyScheduleChange(entry, oldDay, oldStart, oldFacultyName, oldLabName,
                reschedule.getReason().getLabel());

        auditService.record("APPROVE_RESCHEDULE", "Reschedule", rescheduleId,
                oldDay + " " + oldStart + " " + oldFacultyName + " " + oldLabName,
                newDay + " " + newStart + " " + newFaculty.getName() + " " + newLab.getLabName());

        return RescheduleResponse.from(reschedule, candidatesOf(rescheduleId));
    }

    @Transactional
    public RescheduleResponse reject(Long rescheduleId, String reason) {
        Reschedule reschedule = rescheduleRepository.findById(rescheduleId)
                .orElseThrow(() -> ApiException.notFound("Reschedule request", rescheduleId));
        if (reschedule.getStatus() != RescheduleStatus.PROPOSED) {
            throw new ApiException(ErrorCode.RESCHEDULE_STATE_INVALID,
                    "This request has already been " + reschedule.getStatus().name().toLowerCase() + ".");
        }
        reschedule.setStatus(RescheduleStatus.REJECTED);
        reschedule.setReasonDetail(reason == null ? reschedule.getReasonDetail() : reason);
        currentUser.userId().flatMap(userRepository::findById).ifPresent(reschedule::setApprovedBy);
        auditService.record("REJECT_RESCHEDULE", "Reschedule", rescheduleId);
        return RescheduleResponse.from(reschedule, candidatesOf(rescheduleId));
    }

    // ------------------------------------------------------ candidate search

    /**
     * Enumerates every legal alternative and scores it. Ordering follows spec
     * section 22: availability first (enforced by exclusion), then workload,
     * cohort gaps, distance from the original slot, and disruption.
     */
    private List<Option> findOptions(TimetableEntry entry, LocalDate affectedDate) {
        int duration = durationMinutes(entry);
        int students = (int) batchStudentRepository.countByBatchId(entry.getBatch().getId());

        List<DayOfWeek> days = workingDayRepository.findByActiveTrueOrderByDayOrderAsc().stream()
                .map(WorkingDay::getDayOfWeek).toList();
        List<TimeSlot> slots = timeSlotRepository.findAllByOrderBySlotOrderAsc().stream()
                .filter(TimeSlot::isActive).toList();

        List<Faculty> qualified = facultySubjectRepository
                .findQualifiedFacultyIds(entry.getSubject().getId()).stream()
                .map(facultyRepository::findById)
                .flatMap(Optional::stream)
                .filter(f -> f.getStatus() == RecordStatus.ACTIVE)
                .toList();

        List<Laboratory> suitableLabs = labRepository
                .findByLabTypeAndStatus(entry.getBatch().getRequiredLabType(), LabStatus.ACTIVE).stream()
                .filter(l -> l.getCapacity() >= students)
                .toList();

        // Everything currently scheduled, minus the session being moved.
        List<TimetableEntry> others = entryRepository
                .findDetailedByTimetableId(entry.getTimetable().getId()).stream()
                .filter(e -> !e.getId().equals(entry.getId()))
                .toList();

        List<Long> batchIds = new ArrayList<>(others.stream().map(e -> e.getBatch().getId()).toList());
        batchIds.add(entry.getBatch().getId());
        Map<Long, Set<Long>> rosters = loadRosters(batchIds);
        Set<Long> cohort = roster(rosters, entry.getBatch().getId());

        Map<Long, Integer> workload = new HashMap<>();
        others.forEach(e -> workload.merge(e.getFaculty().getId(), durationMinutes(e), Integer::sum));

        Set<String> facultyBlocked = blockedCells(true);
        Set<String> facultyPreferred = preferredCells();
        Set<String> labBlocked = blockedCells(false);

        // A leave cancels one calendar date, not a weekday for ever. Candidates
        // that stay on that weekday are still valid as long as whoever teaches
        // them is available on the date itself - substituting a colleague into
        // the existing slot is usually the least disruptive fix of all, since
        // no student has to change anything.
        DayOfWeek affectedWeekday = affectedDate == null ? null : affectedDate.getDayOfWeek();

        List<Option> options = new ArrayList<>();

        for (DayOfWeek day : days) {
            for (TimeSlot slot : slots) {
                List<TimeSlot> run = contiguousRun(slot, duration);
                if (run.isEmpty()) {
                    continue;
                }
                LocalTime start = run.get(0).getStartTime();
                LocalTime end = start.plusMinutes(duration);

                if (cohortBusy(others, rosters, cohort, day, start, end)) {
                    continue;
                }

                for (Faculty faculty : qualified) {
                    if (blockedIn(facultyBlocked, faculty.getId(), day, run)) {
                        continue;
                    }
                    // Only rules a faculty member out where the session would
                    // still land on the affected date.
                    if (affectedWeekday != null && day == affectedWeekday
                            && leaveRepository.isOnApprovedLeave(faculty.getId(), affectedDate)) {
                        continue;
                    }
                    if (busy(others, day, start, end, e -> e.getFaculty().getId().equals(faculty.getId()))) {
                        continue;
                    }

                    for (Laboratory lab : suitableLabs) {
                        if (blockedIn(labBlocked, lab.getId(), day, run)) {
                            continue;
                        }
                        if (busy(others, day, start, end, e -> e.getLab().getId().equals(lab.getId()))) {
                            continue;
                        }
                        // Never offer the placement it already has.
                        if (day == entry.getDayOfWeek()
                                && start.equals(entry.getStartTime())
                                && faculty.getId().equals(entry.getFaculty().getId())
                                && lab.getId().equals(entry.getLab().getId())) {
                            continue;
                        }

                        options.add(score(entry, day, run, start, end, faculty, lab,
                                workload, facultyPreferred, others, rosters, cohort, days));
                    }
                }
            }
        }

        options.sort(Comparator.comparingDouble(Option::score).reversed());
        return options;
    }

    private Option score(TimetableEntry entry, DayOfWeek day, List<TimeSlot> run,
                         LocalTime start, LocalTime end, Faculty faculty, Laboratory lab,
                         Map<Long, Integer> workload, Set<String> preferred,
                         List<TimetableEntry> others, Map<Long, Set<Long>> rosters,
                         Set<Long> cohort, List<DayOfWeek> days) {

        List<String> notes = new ArrayList<>();
        double score = 100.0;

        // Distance from the original slot - prefer the least disruptive move.
        int dayDistance = Math.abs(days.indexOf(day) - days.indexOf(entry.getDayOfWeek()));
        int slotDistance = Math.abs(run.get(0).getSlotOrder() - entry.getStartTimeSlot().getSlotOrder());
        double distancePenalty = dayDistance * 4.0 + slotDistance * 2.0;
        score -= distancePenalty;
        if (distancePenalty > 0) {
            notes.add(String.format("-%.0f slot distance", distancePenalty));
        }

        // Keeping the same faculty and lab is less disruptive for everyone.
        if (!faculty.getId().equals(entry.getFaculty().getId())) {
            score -= 12;
            notes.add("-12 different faculty");
        }
        if (!lab.getId().equals(entry.getLab().getId())) {
            score -= 5;
            notes.add("-5 different laboratory");
        }

        // Workload: prefer the faculty member with the most headroom.
        int assigned = workload.getOrDefault(faculty.getId(), 0);
        int limit = Math.max(60, faculty.getMaxWeeklyHours() * 60);
        double utilisation = Math.min(1.5, (assigned + durationMinutes(entry)) / (double) limit);
        double workloadPenalty = utilisation * 15.0;
        score -= workloadPenalty;
        notes.add(String.format("-%.0f workload (%.0f%%)", workloadPenalty, utilisation * 100));
        if (assigned + durationMinutes(entry) > limit) {
            score -= 10;
            notes.add("-10 exceeds weekly limit");
        }

        // Faculty preference (soft constraint S1).
        boolean isPreferred = run.stream()
                .allMatch(slot -> preferred.contains(faculty.getId() + "|" + day + "|" + slot.getId()));
        if (isPreferred) {
            score += 8;
            notes.add("+8 preferred slot");
        }

        // Cohort gaps (S3): reward slots adjacent to the cohort's other sessions.
        int gaps = gapsIntroduced(others, rosters, cohort, day, start, end);
        if (gaps > 0) {
            score -= gaps * 6.0;
            notes.add("-" + (gaps * 6) + " student gaps");
        } else {
            score += 4;
            notes.add("+4 no student gaps");
        }

        // Lab right-sizing (S4).
        int students = entry.getBatch().getStudentCount() == null ? 0 : entry.getBatch().getStudentCount();
        int surplus = Math.max(0, lab.getCapacity() - students);
        if (surplus > 0) {
            double surplusPenalty = Math.min(6.0, surplus * 0.3);
            score -= surplusPenalty;
            notes.add(String.format("-%.0f spare seats", surplusPenalty));
        }

        double finalScore = Math.max(0, Math.min(100, Math.round(score * 10) / 10.0));
        return new Option(day, run, start, end, faculty, lab, finalScore, String.join(", ", notes));
    }

    /** Free periods this placement would strand between the cohort's sessions. */
    private int gapsIntroduced(List<TimetableEntry> others, Map<Long, Set<Long>> rosters, Set<Long> cohort,
                               DayOfWeek day, LocalTime start, LocalTime end) {
        List<TimetableEntry> sameDay = others.stream()
                .filter(e -> e.getDayOfWeek() == day)
                .filter(e -> !Collections.disjoint(roster(rosters, e.getBatch().getId()), cohort))
                .sorted(Comparator.comparing(TimetableEntry::getStartTime))
                .toList();
        if (sameDay.isEmpty()) {
            return 0;
        }

        LocalTime earliest = sameDay.get(0).getStartTime();
        LocalTime latest = sameDay.get(sameDay.size() - 1).getEndTime();

        if (end.isBefore(earliest)) {
            return (int) java.time.Duration.between(end, earliest).toHours();
        }
        if (start.isAfter(latest)) {
            return (int) java.time.Duration.between(latest, start).toHours();
        }
        return 0;
    }

    private boolean cohortBusy(List<TimetableEntry> others, Map<Long, Set<Long>> rosters, Set<Long> cohort,
                               DayOfWeek day, LocalTime start, LocalTime end) {
        return others.stream()
                .filter(e -> e.overlaps(day, start, end))
                .anyMatch(e -> !Collections.disjoint(roster(rosters, e.getBatch().getId()), cohort));
    }

    private static boolean busy(List<TimetableEntry> others, DayOfWeek day, LocalTime start, LocalTime end,
                                java.util.function.Predicate<TimetableEntry> match) {
        return others.stream().filter(match).anyMatch(e -> e.overlaps(day, start, end));
    }

    /** Full hard-constraint check for a manually chosen placement. */
    private String firstViolation(TimetableEntry entry, DayOfWeek day, List<TimeSlot> run,
                                  Faculty faculty, Laboratory lab, LocalDate affectedDate) {
        LocalTime start = run.get(0).getStartTime();
        LocalTime end = start.plusMinutes(durationMinutes(entry));
        int students = (int) batchStudentRepository.countByBatchId(entry.getBatch().getId());

        if (!facultySubjectRepository.existsByFacultyIdAndSubjectId(faculty.getId(), entry.getSubject().getId())) {
            return faculty.getName() + " is not qualified to teach " + entry.getSubject().getSubjectName() + ".";
        }
        if (lab.getStatus() != LabStatus.ACTIVE) {
            return lab.getLabName() + " is not active.";
        }
        if (!lab.getLabType().equals(entry.getBatch().getRequiredLabType())) {
            return lab.getLabName() + " is a " + lab.getLabType() + " laboratory but the subject needs "
                    + entry.getBatch().getRequiredLabType() + ".";
        }
        if (lab.getCapacity() < students) {
            return lab.getLabName() + " seats " + lab.getCapacity() + " but the batch has " + students + " students.";
        }
        if (blockedIn(blockedCells(true), faculty.getId(), day, run)) {
            return faculty.getName() + " is marked unavailable in that period.";
        }
        if (blockedIn(blockedCells(false), lab.getId(), day, run)) {
            return lab.getLabName() + " is unavailable in that period.";
        }
        if (affectedDate != null && leaveRepository.isOnApprovedLeave(faculty.getId(), affectedDate)) {
            return faculty.getName() + " is on approved leave on " + affectedDate + ".";
        }

        List<TimetableEntry> others = entryRepository
                .findDetailedByTimetableId(entry.getTimetable().getId()).stream()
                .filter(e -> !e.getId().equals(entry.getId()))
                .toList();

        if (busy(others, day, start, end, e -> e.getFaculty().getId().equals(faculty.getId()))) {
            return faculty.getName() + " already has a practical at that time.";
        }
        if (busy(others, day, start, end, e -> e.getLab().getId().equals(lab.getId()))) {
            return lab.getLabName() + " is already booked at that time.";
        }

        List<Long> batchIds = new ArrayList<>(others.stream().map(e -> e.getBatch().getId()).toList());
        batchIds.add(entry.getBatch().getId());
        Map<Long, Set<Long>> rosters = loadRosters(batchIds);
        if (cohortBusy(others, rosters, roster(rosters, entry.getBatch().getId()), day, start, end)) {
            return "This cohort already has another practical at that time.";
        }

        if (affectedDate != null && !holidayRepository.findByHolidayDate(affectedDate).isEmpty()) {
            return affectedDate + " is a declared holiday.";
        }
        return null;
    }

    // -------------------------------------------------------------- utilities

    /**
     * Batch rosters for one search. Built per call and threaded through the
     * scoring helpers: caching them on the bean would go stale as soon as batch
     * membership changed.
     */
    private Map<Long, Set<Long>> loadRosters(Collection<Long> batchIds) {
        Map<Long, Set<Long>> rosters = new HashMap<>();
        for (Long batchId : new LinkedHashSet<>(batchIds)) {
            Set<Long> students = new HashSet<>();
            batchStudentRepository.findByBatchIdWithStudent(batchId)
                    .forEach(bs -> students.add(bs.getStudent().getId()));
            rosters.put(batchId, students);
        }
        return rosters;
    }

    private static Set<Long> roster(Map<Long, Set<Long>> rosters, Long batchId) {
        return rosters.getOrDefault(batchId, Set.of());
    }

    /** Consecutive active teaching slots starting at {@code first} covering {@code duration}. */
    private List<TimeSlot> contiguousRun(TimeSlot first, int duration) {
        List<TimeSlot> all = timeSlotRepository.findAllByOrderBySlotOrderAsc().stream()
                .filter(TimeSlot::isActive).toList();

        List<TimeSlot> run = new ArrayList<>();
        int covered = 0;
        TimeSlot previous = null;
        for (TimeSlot slot : all) {
            if (slot.getSlotOrder() < first.getSlotOrder()) {
                continue;
            }
            if (!slot.getSlotType().isTeachable()) {
                break;
            }
            if (previous != null && !slot.getStartTime().equals(previous.getEndTime())) {
                break;
            }
            run.add(slot);
            covered += slot.getDurationMinutes();
            previous = slot;
            if (covered == duration) {
                return run;
            }
            if (covered > duration) {
                return List.of();
            }
        }
        return List.of();
    }

    private Set<String> blockedCells(boolean forFaculty) {
        Set<String> cells = new HashSet<>();
        var rows = forFaculty
                ? facultyAvailabilityRepository.findAllNonNeutral()
                : labAvailabilityRepository.findAllNonNeutral();
        rows.forEach(row -> {
            if (row[3] == AvailabilityType.UNAVAILABLE) {
                cells.add(row[0] + "|" + row[1] + "|" + row[2]);
            }
        });
        return cells;
    }

    private Set<String> preferredCells() {
        Set<String> cells = new HashSet<>();
        facultyAvailabilityRepository.findAllNonNeutral().forEach(row -> {
            if (row[3] == AvailabilityType.PREFERRED) {
                cells.add(row[0] + "|" + row[1] + "|" + row[2]);
            }
        });
        return cells;
    }

    private static boolean blockedIn(Set<String> cells, Long id, DayOfWeek day, List<TimeSlot> run) {
        return run.stream().anyMatch(slot -> cells.contains(id + "|" + day + "|" + slot.getId()));
    }

    private static int durationMinutes(TimetableEntry entry) {
        return (int) java.time.Duration.between(entry.getStartTime(), entry.getEndTime()).toMinutes();
    }

    private static <T> T require(T value, String field) {
        if (value == null) {
            throw ApiException.validation("A " + field + " must be supplied for a manual reschedule.");
        }
        return value;
    }
}
