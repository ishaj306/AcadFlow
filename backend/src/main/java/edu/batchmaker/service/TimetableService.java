package edu.batchmaker.service;

import edu.batchmaker.config.BatchmakerProperties;
import edu.batchmaker.domain.entity.*;
import edu.batchmaker.domain.enums.EntryStatus;
import edu.batchmaker.domain.enums.TimetableStatus;
import edu.batchmaker.dto.solver.SolverPayload;
import edu.batchmaker.dto.timeslot.TimeSlotResponse;
import edu.batchmaker.dto.timetable.*;
import edu.batchmaker.exception.ApiException;
import edu.batchmaker.exception.ErrorCode;
import edu.batchmaker.repository.*;
import edu.batchmaker.security.CurrentUser;
import edu.batchmaker.service.solver.FallbackScheduler;
import edu.batchmaker.service.solver.ScheduleDataAssembler;
import edu.batchmaker.service.solver.SolverClient;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Timetable generation workflow (spec section 18).
 *
 * <p>Generation always produces a DRAFT. The result is re-validated against the
 * database by {@link ConflictDetectionService} before anything is offered for
 * approval, and only an explicit approval with zero hard violations publishes
 * it. Nothing is ever published automatically.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimetableService {

    private final TimetableRepository timetableRepository;
    private final TimetableEntryRepository entryRepository;
    private final PracticalRepository practicalRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final WorkingDayRepository workingDayRepository;
    private final StudentBatchRepository batchRepository;
    private final SubjectRepository subjectRepository;
    private final FacultyRepository facultyRepository;
    private final LaboratoryRepository labRepository;
    private final BatchStudentRepository batchStudentRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    private final ScheduleDataAssembler assembler;
    private final SolverClient solverClient;
    private final FallbackScheduler fallbackScheduler;
    private final ConflictDetectionService conflictService;
    private final NotificationService notificationService;
    private final MasterDataService masterDataService;
    private final AuditService auditService;
    private final CurrentUser currentUser;
    private final BatchmakerProperties properties;

    // ------------------------------------------------------------- generation

    /** The three optimisation profiles offered by "Generate 3 options". */
    private record OptionProfile(String label, SolverPayload.Weights weights) {
    }

    private static final List<OptionProfile> OPTION_PROFILES = List.of(
            // Balanced keeps the installation's configured weights (null = leave as-is).
            new OptionProfile("Balanced", null),
            // Faculty-friendly: heavier workload balance and less idle time between classes.
            new OptionProfile("Faculty-friendly",
                    new SolverPayload.Weights(12.0, 2.0, 3.0, 1.0, 10.0, 5.0)),
            // Lab-efficient: pack labs tightly, tolerate more uneven faculty load.
            new OptionProfile("Lab-efficient",
                    new SolverPayload.Weights(4.0, 2.0, 2.0, 6.0, 10.0, 1.0)));

    @Transactional
    public GenerationResultResponse generate(TimetableGenerateRequest request) {
        AcademicTerm term = masterDataService.requireCurrentTerm();
        requirePracticals(term);

        double maxSeconds = request.maxSeconds() == null ? 30 : request.maxSeconds();
        var snapshot = assembler.assemble(term, maxSeconds, List.of(), List.of());

        SolverPayload.Request solverRequest = request.weights() == null
                ? snapshot.request()
                : withWeights(snapshot.request(), toWeights(request.weights()));

        return solveAndPersist(request, term, snapshot, solverRequest);
    }

    /**
     * Produces several draft timetables in one call, each optimised for a
     * different priority, so the user can compare and approve whichever fits
     * best. Each draft is a real DRAFT timetable, validated like any other.
     */
    @Transactional
    public List<GenerationResultResponse> generateOptions(TimetableGenerateRequest request) {
        AcademicTerm term = masterDataService.requireCurrentTerm();
        requirePracticals(term);

        // Three solves share one time budget, so cap each so the whole call
        // stays inside the solver's HTTP read timeout.
        double requested = request.maxSeconds() == null ? 30 : request.maxSeconds();
        double perOption = Math.min(requested, 30);
        var snapshot = assembler.assemble(term, perOption, List.of(), List.of());

        String baseName = (request.name() == null || request.name().isBlank())
                ? "Timetable " + term.getLabel()
                : request.name().trim();

        List<GenerationResultResponse> results = new ArrayList<>();
        for (OptionProfile profile : OPTION_PROFILES) {
            SolverPayload.Request solverRequest = profile.weights() == null
                    ? snapshot.request()
                    : withWeights(snapshot.request(), profile.weights());
            var optionRequest = new TimetableGenerateRequest(
                    baseName + " — " + profile.label(), request.departmentId(),
                    (int) perOption, null);
            try {
                results.add(solveAndPersist(optionRequest, term, snapshot, solverRequest));
            } catch (ApiException ex) {
                // One infeasible profile should not sink the others.
                log.warn("Option '{}' could not be generated: {}", profile.label(), ex.getMessage());
            }
        }
        if (results.isEmpty()) {
            throw new ApiException(ErrorCode.NO_FEASIBLE_SCHEDULE,
                    "No feasible timetable option could be generated with the current data.");
        }
        return results;
    }

    private void requirePracticals(AcademicTerm term) {
        if (practicalRepository.findActiveForTerm(term.getId()).isEmpty()) {
            throw new ApiException(ErrorCode.NO_FEASIBLE_SCHEDULE,
                    "There are no practicals to schedule. Generate practical batches first.");
        }
    }

    /** Shared solve → persist → validate pipeline used by both entry points. */
    private GenerationResultResponse solveAndPersist(TimetableGenerateRequest request, AcademicTerm term,
                                                     ScheduleDataAssembler.Snapshot snapshot,
                                                     SolverPayload.Request solverRequest) {
        SolverPayload.Response solverResponse;
        String engine;
        try {
            solverResponse = solverClient.solve(solverRequest);
            engine = solverResponse.engine() == null ? "ortools-cpsat" : solverResponse.engine();
        } catch (SolverClient.SolverUnavailableException ex) {
            if (!properties.getSolver().isFallbackEnabled()) {
                throw new ApiException(ErrorCode.SOLVER_UNAVAILABLE, ex.getMessage());
            }
            log.warn("Optimisation service unavailable, using the built-in fallback scheduler: {}",
                    ex.getMessage());
            solverResponse = fallbackScheduler.solve(solverRequest);
            engine = "builtin-greedy-fallback";
        }

        if (solverResponse == null) {
            throw new ApiException(ErrorCode.SOLVER_FAILED, "The optimisation service returned no result.");
        }

        List<String> solverViolations = solverResponse.violations() == null ? List.of()
                : solverResponse.violations().stream().map(v -> v.message()).toList();

        if (!"SUCCESS".equals(solverResponse.status())) {
            throw new ApiException(ErrorCode.NO_FEASIBLE_SCHEDULE,
                    solverViolations.isEmpty()
                            ? "The optimizer could not produce a feasible schedule."
                            : solverViolations.get(0),
                    Map.of("status", solverResponse.status(), "details", solverViolations));
        }

        Timetable timetable = persist(request, term, solverResponse, engine, snapshot);

        // Independent re-check; the stored violation count comes from here, not
        // from the solver's own report.
        conflictService.detectAndStore(timetable.getId());
        ValidationSummary validation = conflictService.validate(timetable.getId());

        auditService.record("GENERATE", "Timetable", timetable.getId(), null,
                "engine=" + engine + " score=" + timetable.getScore()
                        + " entries=" + entryRepository.countByTimetableId(timetable.getId()));

        var breakdown = solverResponse.breakdown();
        var metrics = new GenerationResultResponse.SolverMetrics(
                engine,
                solverResponse.runtimeMs() == null ? 0 : solverResponse.runtimeMs(),
                solverResponse.scheduleScore() == null ? 0 : solverResponse.scheduleScore(),
                breakdown == null ? null : breakdown.workloadImbalanceMinutes(),
                breakdown == null ? null : breakdown.facultyPreferenceViolations(),
                breakdown == null ? null : breakdown.studentGapSlots(),
                breakdown == null ? null : breakdown.facultyIdleSlots(),
                breakdown == null ? null : breakdown.labSurplusSeats(),
                breakdown == null ? null : breakdown.scheduleChanges());

        return new GenerationResultResponse(
                "SUCCESS",
                detail(timetable.getId(), validation),
                solverResponse.warnings() == null ? List.of() : solverResponse.warnings(),
                solverViolations,
                metrics);
    }

    /** Copies a solver request with a different objective-weight vector. */
    private SolverPayload.Request withWeights(SolverPayload.Request base, SolverPayload.Weights weights) {
        return new SolverPayload.Request(
                base.days(), base.timeSlots(), base.labs(), base.faculty(), base.practicals(),
                base.studentConflictGroups(), base.studentCohorts(), base.locked(), base.previous(),
                weights, base.maxSeconds(), base.workers());
    }

    /** UI weight overrides (camelCase) to the solver's weight record. */
    private SolverPayload.Weights toWeights(TimetableGenerateRequest.WeightOverrides o) {
        SolverPayload.Weights defaults = assembler.weights();
        return new SolverPayload.Weights(
                o.workloadImbalance() == null ? defaults.workloadImbalance() : o.workloadImbalance(),
                o.facultyPreference() == null ? defaults.facultyPreference() : o.facultyPreference(),
                o.studentGaps() == null ? defaults.studentGaps() : o.studentGaps(),
                o.labUnderutilization() == null ? defaults.labUnderutilization() : o.labUnderutilization(),
                o.scheduleChanges() == null ? defaults.scheduleChanges() : o.scheduleChanges(),
                o.facultyIdle() == null ? defaults.facultyIdle() : o.facultyIdle());
    }

    /**
     * Explains whether the current data can be scheduled at all, before anyone
     * spends time generating. Pure resource arithmetic in the solver, enriched
     * here with the subject, batch and division names the UI shows.
     */
    @Transactional(readOnly = true)
    public FeasibilityResponse feasibilityAudit() {
        AcademicTerm term = masterDataService.requireCurrentTerm();

        List<Practical> practicals = practicalRepository.findActiveForTerm(term.getId());
        if (practicals.isEmpty()) {
            throw new ApiException(ErrorCode.NO_FEASIBLE_SCHEDULE,
                    "There are no practicals to schedule. Generate practical batches first.");
        }

        var snapshot = assembler.assemble(term, 1, List.of(), List.of());
        return enrich(callFeasibility(snapshot.request()), practicals, snapshot.practicalsById());
    }

    /**
     * Compares the current data (baseline) against a hypothetical scenario -
     * a closed lab, an absent faculty member, or a rise in enrolment - and shows
     * how feasibility changes, without touching any saved data.
     */
    @Transactional(readOnly = true)
    public WhatIfResponse whatIf(WhatIfScenario scenario) {
        AcademicTerm term = masterDataService.requireCurrentTerm();

        List<Practical> practicals = practicalRepository.findActiveForTerm(term.getId());
        if (practicals.isEmpty()) {
            throw new ApiException(ErrorCode.NO_FEASIBLE_SCHEDULE,
                    "There are no practicals to schedule. Generate practical batches first.");
        }

        var snapshot = assembler.assemble(term, 1, List.of(), List.of());
        Map<Long, Practical> byPractical = snapshot.practicalsById();

        FeasibilityResponse baseline =
                enrich(callFeasibility(snapshot.request()), practicals, byPractical);
        SolverPayload.Request modified = applyScenario(snapshot.request(), scenario);
        FeasibilityResponse simulated = enrich(callFeasibility(modified), practicals, byPractical);

        return new WhatIfResponse(baseline, simulated, describeScenario(scenario, snapshot));
    }

    /** Calls the solver's feasibility endpoint, turning transport failures into API errors. */
    private SolverPayload.FeasibilityReport callFeasibility(SolverPayload.Request request) {
        SolverPayload.FeasibilityReport report;
        try {
            report = solverClient.feasibility(request);
        } catch (SolverClient.SolverUnavailableException ex) {
            throw new ApiException(ErrorCode.SOLVER_UNAVAILABLE, ex.getMessage());
        }
        if (report == null) {
            throw new ApiException(ErrorCode.SOLVER_FAILED,
                    "The optimisation service returned no feasibility result.");
        }
        return report;
    }

    /** Builds a scenario variant of the solver request: closed labs, absent faculty, more students. */
    private SolverPayload.Request applyScenario(SolverPayload.Request base, WhatIfScenario scenario) {
        Set<Long> closedLabs = new HashSet<>(nullSafe(scenario.closedLabIds()));
        Set<Long> absentFaculty = new HashSet<>(nullSafe(scenario.absentFacultyIds()));
        int percent = scenario.additionalStudentPercent() == null ? 0 : scenario.additionalStudentPercent();
        double factor = 1.0 + Math.max(0, percent) / 100.0;

        List<SolverPayload.LabIn> labs = base.labs().stream()
                .filter(l -> !closedLabs.contains(l.id())).toList();
        List<SolverPayload.FacultyIn> faculty = base.faculty().stream()
                .filter(f -> !absentFaculty.contains(f.id())).toList();
        List<SolverPayload.PracticalIn> practicals = base.practicals().stream()
                .map(p -> new SolverPayload.PracticalIn(
                        p.id(), p.subjectId(), p.batchId(),
                        (int) Math.ceil(p.studentCount() * factor),
                        p.requiredLabType(), p.durationMinutes(), p.sessionsPerWeek(), p.preferredFacultyId()))
                .toList();

        return new SolverPayload.Request(
                base.days(), base.timeSlots(), labs, faculty, practicals,
                base.studentConflictGroups(), base.studentCohorts(), base.locked(), base.previous(),
                base.weights(), base.maxSeconds(), base.workers());
    }

    /** Human-readable list of what the scenario changed, for the UI. */
    private List<String> describeScenario(WhatIfScenario scenario, ScheduleDataAssembler.Snapshot snapshot) {
        List<String> changes = new ArrayList<>();
        for (Long labId : nullSafe(scenario.closedLabIds())) {
            Laboratory lab = snapshot.labsById().get(labId);
            changes.add("Closed " + (lab == null ? "lab " + labId : lab.getLabName()));
        }
        for (Long facultyId : nullSafe(scenario.absentFacultyIds())) {
            Faculty f = snapshot.facultyById().get(facultyId);
            changes.add((f == null ? "Faculty " + facultyId : f.getName()) + " absent");
        }
        int percent = scenario.additionalStudentPercent() == null ? 0 : scenario.additionalStudentPercent();
        if (percent > 0) {
            changes.add("+" + percent + "% student intake");
        }
        if (changes.isEmpty()) {
            changes.add("No changes applied — baseline and simulation are identical.");
        }
        return changes;
    }

    /** Attaches the subject, batch and division names the UI needs to a raw solver report. */
    private FeasibilityResponse enrich(SolverPayload.FeasibilityReport report,
                                       List<Practical> practicals, Map<Long, Practical> byPractical) {
        Map<Long, StudentBatch> batchById = new HashMap<>();
        Map<Long, String> subjectNames = new HashMap<>();
        for (Practical p : practicals) {
            batchById.putIfAbsent(p.getBatch().getId(), p.getBatch());
            subjectNames.putIfAbsent(p.getSubject().getId(), p.getSubject().getSubjectName());
        }

        List<FeasibilityResponse.Blocker> blockers = nullSafe(report.blockers()).stream()
                .map(b -> {
                    Practical p = b.practicalId() == null ? null : byPractical.get(b.practicalId());
                    return new FeasibilityResponse.Blocker(
                            b.code(), b.message(), b.practicalId(),
                            p == null ? null : p.getSubject().getSubjectName(),
                            p == null ? null : p.getBatch().getBatchName(),
                            p == null ? null : p.getBatch().getDivision());
                })
                .toList();

        List<FeasibilityResponse.Check> checks = nullSafe(report.resourceChecks()).stream()
                .map(c -> new FeasibilityResponse.Check(
                        c.key(), humaniseCheckLabel(c.key(), c.label(), subjectNames),
                        c.required() == null ? 0 : c.required(),
                        c.available() == null ? 0 : c.available(),
                        c.unit(), c.satisfied(),
                        c.utilizationPercent() == null ? 0 : c.utilizationPercent(),
                        c.detail()))
                .toList();

        List<FeasibilityResponse.BatchLoad> loads = nullSafe(report.batchLoads()).stream()
                .map(l -> {
                    StudentBatch batch = batchById.get(l.batchId());
                    return new FeasibilityResponse.BatchLoad(
                            l.batchId(),
                            batch == null ? ("Batch " + l.batchId()) : batch.getBatchName(),
                            batch == null ? null : batch.getDivision(),
                            l.sessionsRequired() == null ? 0 : l.sessionsRequired(),
                            l.labType());
                })
                .toList();

        List<FeasibilityResponse.Suggestion> suggestions = nullSafe(report.suggestions()).stream()
                .map(s -> new FeasibilityResponse.Suggestion(
                        s.code(), s.title(), s.detail(), s.category(), s.estimatedGainSessions()))
                .toList();

        return new FeasibilityResponse(
                report.verdict(),
                report.totalSessionsRequired() == null ? 0 : report.totalSessionsRequired(),
                report.totalSessionCapacity() == null ? 0 : report.totalSessionCapacity(),
                blockers, checks, loads, suggestions,
                nullSafe(report.notes()),
                report.runtimeMs() == null ? 0 : report.runtimeMs());
    }

    /** Replace the id-based per-subject label with the subject's real name. */
    private String humaniseCheckLabel(String key, String label, Map<Long, String> subjectNames) {
        if (key != null && key.startsWith("faculty:subject:")) {
            try {
                long subjectId = Long.parseLong(key.substring("faculty:subject:".length()));
                String name = subjectNames.get(subjectId);
                if (name != null) {
                    return "Faculty qualified for " + name;
                }
            } catch (NumberFormatException ignored) {
                // fall through to the raw label
            }
        }
        return label;
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private Timetable persist(TimetableGenerateRequest request, AcademicTerm term,
                              SolverPayload.Response response, String engine,
                              ScheduleDataAssembler.Snapshot snapshot) {

        Timetable timetable = new Timetable();
        timetable.setName(request.name() == null || request.name().isBlank()
                ? "Practical Timetable " + term.getLabel()
                : request.name().trim());
        timetable.setAcademicTerm(term);
        if (request.departmentId() != null) {
            timetable.setDepartment(departmentRepository.findById(request.departmentId()).orElse(null));
        }
        timetable.setStatus(TimetableStatus.DRAFT);
        timetable.setScore(response.scheduleScore());
        timetable.setSolverStatus(response.status());
        timetable.setSolverEngine(engine);
        timetable.setSolverRuntimeMs(response.runtimeMs());
        timetable.setGeneratedAt(Instant.now());
        currentUser.userId().flatMap(userRepository::findById).ifPresent(timetable::setGeneratedBy);
        if (response.breakdown() != null) {
            timetable.setSoftPenalty(response.breakdown().weightedPenalty());
        }
        Timetable saved = timetableRepository.save(timetable);

        Map<Long, TimeSlot> slots = snapshot.slotsById();
        for (SolverPayload.AssignmentOut assignment : response.assignments()) {
            Practical practical = snapshot.practicalsById().get(assignment.practicalId());
            if (practical == null) {
                log.warn("Solver returned an unknown practical id {}; skipping", assignment.practicalId());
                continue;
            }

            TimetableEntry entry = new TimetableEntry();
            entry.setTimetable(saved);
            entry.setPractical(practical);
            entry.setBatch(practical.getBatch());
            entry.setSubject(practical.getSubject());
            entry.setFaculty(facultyRepository.findById(assignment.facultyId())
                    .orElseThrow(() -> ApiException.notFound("Faculty", assignment.facultyId())));
            entry.setLab(labRepository.findById(assignment.labId())
                    .orElseThrow(() -> ApiException.notFound("Laboratory", assignment.labId())));
            entry.setDayOfWeek(DayOfWeek.valueOf(assignment.day()));
            entry.setStartTimeSlot(slots.get(assignment.startSlotId()));
            entry.setEndTimeSlot(slots.get(assignment.endSlotId()));
            entry.setStartTime(LocalTime.parse(assignment.startTime()));
            entry.setEndTime(LocalTime.parse(assignment.endTime()));
            entry.setSessionIndex(assignment.sessionIndex() == null ? 1 : assignment.sessionIndex() + 1);
            entry.setStatus(EntryStatus.SCHEDULED);
            entryRepository.save(entry);
        }
        return saved;
    }

    // -------------------------------------------------------------- retrieval

    @Transactional(readOnly = true)
    public List<TimetableSummaryResponse> list() {
        return timetableRepository.findAllByOrderByGeneratedAtDesc().stream()
                .map(t -> TimetableSummaryResponse.from(t, entryRepository.countByTimetableId(t.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TimetableDetailResponse detail(Long timetableId) {
        return detail(timetableId, conflictService.validate(timetableId));
    }

    private TimetableDetailResponse detail(Long timetableId, ValidationSummary validation) {
        Timetable timetable = timetableRepository.findById(timetableId)
                .orElseThrow(() -> ApiException.notFound("Timetable", timetableId));
        List<TimetableEntry> entries = entryRepository.findDetailedByTimetableId(timetableId);

        return new TimetableDetailResponse(
                TimetableSummaryResponse.from(timetable, entries.size()),
                activeDays(),
                timeSlotRepository.findAllByOrderBySlotOrderAsc().stream()
                        .filter(TimeSlot::isActive)
                        .map(TimeSlotResponse::from)
                        .toList(),
                entries.stream().map(TimetableEntryResponse::from).toList(),
                validation);
    }

    /** The schedule currently in force, or null when nothing is published yet. */
    @Transactional(readOnly = true)
    public TimetableDetailResponse current() {
        Timetable published = timetableRepository
                .findFirstByStatusOrderByPublishedAtDesc(TimetableStatus.PUBLISHED)
                .orElseThrow(() -> new ApiException(ErrorCode.TIMETABLE_NOT_PUBLISHED,
                        "No timetable has been published yet."));
        return detail(published.getId());
    }

    @Transactional(readOnly = true)
    public Optional<Timetable> currentPublished() {
        return timetableRepository.findFirstByStatusOrderByPublishedAtDesc(TimetableStatus.PUBLISHED);
    }

    // ---------------------------------------------------------------- approval

    @Transactional
    public TimetableDetailResponse approve(Long timetableId) {
        Timetable timetable = timetableRepository.findById(timetableId)
                .orElseThrow(() -> ApiException.notFound("Timetable", timetableId));

        if (timetable.getStatus() == TimetableStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.TIMETABLE_ALREADY_PUBLISHED,
                    "This timetable is already published.");
        }

        // Re-validate at the moment of approval: data may have changed since
        // generation, so an earlier clean preview is not sufficient.
        conflictService.detectAndStore(timetableId);
        ValidationSummary validation = conflictService.validate(timetableId);

        if (!validation.publishable()) {
            throw new ApiException(ErrorCode.TIMETABLE_HAS_VIOLATIONS,
                    "This timetable still has " + validation.hardViolations()
                            + " hard constraint violation(s) and cannot be published. "
                            + "Resolve them or regenerate.",
                    Map.of("hardViolations", validation.hardViolations()));
        }

        // Supersede whatever was live before.
        timetableRepository.findByStatusOrderByGeneratedAtDesc(TimetableStatus.PUBLISHED)
                .forEach(previous -> previous.setStatus(TimetableStatus.ARCHIVED));

        Instant now = Instant.now();
        timetable.setStatus(TimetableStatus.PUBLISHED);
        timetable.setApprovedAt(now);
        timetable.setPublishedAt(now);
        currentUser.userId().flatMap(userRepository::findById).ifPresent(timetable::setApprovedBy);

        long entryCount = entryRepository.countByTimetableId(timetableId);
        notificationService.notifyTimetablePublished(timetable.getName(), timetableId, (int) entryCount);
        auditService.record("APPROVE_AND_PUBLISH", "Timetable", timetableId, "DRAFT", "PUBLISHED");

        return detail(timetableId, validation);
    }

    @Transactional
    public TimetableSummaryResponse reject(Long timetableId, String reason) {
        Timetable timetable = timetableRepository.findById(timetableId)
                .orElseThrow(() -> ApiException.notFound("Timetable", timetableId));
        if (timetable.getStatus() == TimetableStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.TIMETABLE_ALREADY_PUBLISHED,
                    "A published timetable cannot be rejected. Publish a replacement instead.");
        }
        timetable.setStatus(TimetableStatus.REJECTED);
        timetable.setNotes(reason);
        auditService.record("REJECT", "Timetable", timetableId, "DRAFT", "REJECTED");
        return TimetableSummaryResponse.from(timetable, entryRepository.countByTimetableId(timetableId));
    }

    @Transactional
    public void delete(Long timetableId) {
        Timetable timetable = timetableRepository.findById(timetableId)
                .orElseThrow(() -> ApiException.notFound("Timetable", timetableId));
        if (timetable.getStatus() == TimetableStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE,
                    "A published timetable cannot be deleted. Archive it by publishing a replacement.");
        }
        auditService.record("DELETE", "Timetable", timetableId, timetable.getName(), null);
        timetableRepository.delete(timetable);
    }

    @Transactional(readOnly = true)
    public ValidationSummary validate(Long timetableId) {
        return conflictService.validate(timetableId);
    }

    // ---------------------------------------------------------- manual editing

    /** Adds one session to a draft, then re-validates the whole timetable. */
    @Transactional
    public TimetableDetailResponse addEntry(Long timetableId, EntryEditRequest request) {
        Timetable timetable = requireDraft(timetableId);
        TimetableEntry entry = new TimetableEntry();
        entry.setTimetable(timetable);
        applyEntryFields(entry, request);
        entryRepository.save(entry);
        auditService.record("ADD_ENTRY", "Timetable", timetableId, null,
                "practical=" + request.practicalId() + " " + request.dayOfWeek());
        return revalidated(timetableId);
    }

    /** Moves or reassigns one session on a draft, then re-validates. */
    @Transactional
    public TimetableDetailResponse updateEntry(Long timetableId, Long entryId, EntryEditRequest request) {
        Timetable timetable = requireDraft(timetableId);
        TimetableEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> ApiException.notFound("Timetable entry", entryId));
        if (!entry.getTimetable().getId().equals(timetable.getId())) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE,
                    "That session does not belong to this timetable.");
        }
        applyEntryFields(entry, request);
        entry.setStatus(EntryStatus.RESCHEDULED);
        entryRepository.save(entry);
        auditService.record("EDIT_ENTRY", "Timetable", timetableId, null, "entry=" + entryId);
        return revalidated(timetableId);
    }

    /** Removes one session from a draft, then re-validates. */
    @Transactional
    public TimetableDetailResponse deleteEntry(Long timetableId, Long entryId) {
        Timetable timetable = requireDraft(timetableId);
        TimetableEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> ApiException.notFound("Timetable entry", entryId));
        if (!entry.getTimetable().getId().equals(timetable.getId())) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE,
                    "That session does not belong to this timetable.");
        }
        entryRepository.delete(entry);
        auditService.record("DELETE_ENTRY", "Timetable", timetableId, "entry=" + entryId, null);
        return revalidated(timetableId);
    }

    private Timetable requireDraft(Long timetableId) {
        Timetable timetable = timetableRepository.findById(timetableId)
                .orElseThrow(() -> ApiException.notFound("Timetable", timetableId));
        if (timetable.getStatus() != TimetableStatus.DRAFT) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE,
                    "Only draft timetables can be edited. Regenerate or create a new draft to make changes.");
        }
        return timetable;
    }

    /** Fills an entry from the request, deriving batch, subject and times from the choices. */
    private void applyEntryFields(TimetableEntry entry, EntryEditRequest request) {
        Practical practical = practicalRepository.findById(request.practicalId())
                .orElseThrow(() -> ApiException.notFound("Practical", request.practicalId()));
        TimeSlot startSlot = timeSlotRepository.findById(request.startSlotId())
                .orElseThrow(() -> ApiException.notFound("Time slot", request.startSlotId()));
        TimeSlot endSlot = timeSlotRepository.findById(request.endSlotId())
                .orElseThrow(() -> ApiException.notFound("Time slot", request.endSlotId()));
        DayOfWeek day;
        try {
            day = DayOfWeek.valueOf(request.dayOfWeek());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown day: " + request.dayOfWeek());
        }
        if (startSlot.getStartTime().isAfter(endSlot.getEndTime())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "The start period must not come after the end period.");
        }

        entry.setPractical(practical);
        entry.setBatch(practical.getBatch());
        entry.setSubject(practical.getSubject());
        entry.setFaculty(facultyRepository.findById(request.facultyId())
                .orElseThrow(() -> ApiException.notFound("Faculty", request.facultyId())));
        entry.setLab(labRepository.findById(request.labId())
                .orElseThrow(() -> ApiException.notFound("Laboratory", request.labId())));
        entry.setDayOfWeek(day);
        entry.setStartTimeSlot(startSlot);
        entry.setEndTimeSlot(endSlot);
        entry.setStartTime(startSlot.getStartTime());
        entry.setEndTime(endSlot.getEndTime());
        if (entry.getSessionIndex() == null) {
            entry.setSessionIndex(1);
        }
        if (entry.getStatus() == null) {
            entry.setStatus(EntryStatus.SCHEDULED);
        }
    }

    /** Re-runs conflict detection after a manual change and returns the fresh detail. */
    private TimetableDetailResponse revalidated(Long timetableId) {
        conflictService.detectAndStore(timetableId);
        return detail(timetableId, conflictService.validate(timetableId));
    }

    // ------------------------------------------------------------ scoped views

    @Transactional(readOnly = true)
    public TimetableDetailResponse forFaculty(Long facultyId) {
        Timetable timetable = requirePublished();
        return scoped(timetable, entryRepository.findDetailedByTimetableId(timetable.getId()).stream()
                .filter(e -> e.getFaculty().getId().equals(facultyId))
                .toList());
    }

    @Transactional(readOnly = true)
    public TimetableDetailResponse forLab(Long labId) {
        Timetable timetable = requirePublished();
        return scoped(timetable, entryRepository.findDetailedByTimetableId(timetable.getId()).stream()
                .filter(e -> e.getLab().getId().equals(labId))
                .toList());
    }

    @Transactional(readOnly = true)
    public TimetableDetailResponse forBatch(Long batchId) {
        Timetable timetable = requirePublished();
        return scoped(timetable, entryRepository.findDetailedByTimetableId(timetable.getId()).stream()
                .filter(e -> e.getBatch().getId().equals(batchId))
                .toList());
    }

    /** Every practical a student attends, across all of their subject batches. */
    @Transactional(readOnly = true)
    public TimetableDetailResponse forStudent(Long studentId) {
        Timetable timetable = requirePublished();
        Set<Long> batchIds = new HashSet<>();
        batchStudentRepository.findByStudentIdWithBatch(studentId)
                .forEach(bs -> batchIds.add(bs.getBatch().getId()));

        return scoped(timetable, entryRepository.findDetailedByTimetableId(timetable.getId()).stream()
                .filter(e -> batchIds.contains(e.getBatch().getId()))
                .toList());
    }

    @Transactional(readOnly = true)
    public TimetableDetailResponse forDivision(Long departmentId, Integer semester, String division) {
        Timetable timetable = requirePublished();
        return scoped(timetable, entryRepository.findDetailedByTimetableId(timetable.getId()).stream()
                .filter(e -> e.getBatch().getDivision().equalsIgnoreCase(division))
                .filter(e -> semester == null || e.getBatch().getSemester().equals(semester))
                .filter(e -> departmentId == null || e.getBatch().getDepartment().getId().equals(departmentId))
                .toList());
    }

    private TimetableDetailResponse scoped(Timetable timetable, List<TimetableEntry> entries) {
        return new TimetableDetailResponse(
                TimetableSummaryResponse.from(timetable, entries.size()),
                activeDays(),
                timeSlotRepository.findAllByOrderBySlotOrderAsc().stream()
                        .filter(TimeSlot::isActive)
                        .map(TimeSlotResponse::from)
                        .toList(),
                entries.stream().map(TimetableEntryResponse::from).toList(),
                null);
    }

    private Timetable requirePublished() {
        return timetableRepository.findFirstByStatusOrderByPublishedAtDesc(TimetableStatus.PUBLISHED)
                .orElseThrow(() -> new ApiException(ErrorCode.TIMETABLE_NOT_PUBLISHED,
                        "No timetable has been published yet."));
    }

    private List<DayOfWeek> activeDays() {
        return workingDayRepository.findByActiveTrueOrderByDayOrderAsc().stream()
                .map(WorkingDay::getDayOfWeek).toList();
    }
}
