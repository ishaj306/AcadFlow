package edu.batchmaker.service;

import edu.batchmaker.domain.entity.*;
import edu.batchmaker.domain.enums.AvailabilityType;
import edu.batchmaker.domain.enums.LeaveStatus;
import edu.batchmaker.domain.enums.Proficiency;
import edu.batchmaker.domain.enums.RecordStatus;
import edu.batchmaker.dto.common.IdNameResponse;
import edu.batchmaker.dto.common.ImportResult;
import edu.batchmaker.dto.common.PageResponse;
import edu.batchmaker.dto.faculty.*;
import edu.batchmaker.exception.ApiException;
import edu.batchmaker.exception.ErrorCode;
import edu.batchmaker.repository.*;
import edu.batchmaker.security.CurrentUser;
import edu.batchmaker.service.support.CsvSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class FacultyService {

    private final FacultyRepository facultyRepository;
    private final FacultySubjectRepository facultySubjectRepository;
    private final FacultyAvailabilityRepository availabilityRepository;
    private final FacultyLeaveRepository leaveRepository;
    private final DepartmentRepository departmentRepository;
    private final SubjectRepository subjectRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<FacultyResponse> search(String search, Long departmentId,
                                                RecordStatus status, Pageable pageable) {
        return PageResponse.from(
                facultyRepository.search(blankToNull(search), departmentId, status, pageable),
                faculty -> FacultyResponse.from(faculty, subjectsOf(faculty.getId())));
    }

    @Transactional(readOnly = true)
    public List<FacultyResponse> listActive() {
        return facultyRepository.findByStatusOrderByNameAsc(RecordStatus.ACTIVE).stream()
                .map(faculty -> FacultyResponse.from(faculty, subjectsOf(faculty.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public FacultyResponse get(Long id) {
        Faculty faculty = find(id);
        return FacultyResponse.from(faculty, subjectsOf(id));
    }

    @Transactional
    public FacultyResponse create(FacultyRequest request) {
        if (facultyRepository.existsByEmployeeCode(request.employeeCode())) {
            throw ApiException.duplicate("Employee code '" + request.employeeCode() + "' already exists.");
        }
        if (facultyRepository.existsByEmail(request.email())) {
            throw ApiException.duplicate("Email '" + request.email() + "' is already registered.");
        }

        Faculty faculty = new Faculty();
        apply(faculty, request);
        Faculty saved = facultyRepository.save(faculty);
        replaceQualifications(saved, request.subjectIds());
        auditService.record("CREATE", "Faculty", saved.getId(), null, describe(saved));
        return FacultyResponse.from(saved, subjectsOf(saved.getId()));
    }

    @Transactional
    public FacultyResponse update(Long id, FacultyRequest request) {
        Faculty faculty = find(id);
        String before = describe(faculty);

        if (facultyRepository.existsByEmployeeCodeAndIdNot(request.employeeCode(), id)) {
            throw ApiException.duplicate("Employee code '" + request.employeeCode() + "' belongs to another record.");
        }
        if (facultyRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw ApiException.duplicate("Email '" + request.email() + "' belongs to another record.");
        }

        apply(faculty, request);
        if (request.subjectIds() != null) {
            replaceQualifications(faculty, request.subjectIds());
        }
        auditService.record("UPDATE", "Faculty", id, before, describe(faculty));
        return FacultyResponse.from(faculty, subjectsOf(id));
    }

    @Transactional
    public void deactivate(Long id) {
        Faculty faculty = find(id);
        faculty.setStatus(RecordStatus.INACTIVE);
        auditService.record("DEACTIVATE", "Faculty", id);
    }

    // ---------------------------------------------------------------- import

    private static final List<String> REQUIRED_CSV_HEADERS = List.of(
            "employee_code", "name", "email", "department_code", "designation", "max_weekly_hours");

    /**
     * Bulk CSV upload of faculty. Rows are validated independently; a bad row is
     * reported and skipped rather than aborting the whole import. The optional
     * {@code subject_codes} column (a {@code ;}-separated list of subject codes)
     * sets the subjects each member is qualified to teach — an unknown code fails
     * only that row.
     */
    @Transactional
    public ImportResult importCsv(MultipartFile file, boolean updateExisting) {
        CsvSupport.Sheet sheet = CsvSupport.read(file, REQUIRED_CSV_HEADERS);
        Map<String, Department> departmentCache = new HashMap<>();
        List<ImportResult.RowError> errors = new ArrayList<>();
        int imported = 0;
        int updated = 0;

        for (CsvSupport.Row row : sheet.rows()) {
            String code = CsvSupport.value(row.cells(), sheet.columns(), "employee_code");
            try {
                switch (upsertRow(row.cells(), sheet.columns(), departmentCache, updateExisting)) {
                    case INSERTED -> imported++;
                    case UPDATED -> updated++;
                    case SKIPPED_DUPLICATE -> errors.add(new ImportResult.RowError(row.number(), code,
                            "Employee code already exists. Enable 'update existing' to overwrite."));
                }
            } catch (ApiException ex) {
                errors.add(new ImportResult.RowError(row.number(), code, ex.getMessage()));
            } catch (RuntimeException ex) {
                errors.add(new ImportResult.RowError(row.number(), code,
                        "Row could not be processed: " + ex.getMessage()));
            }
        }

        auditService.record("IMPORT", "Faculty", null, null,
                "imported=" + imported + " updated=" + updated + " errors=" + errors.size());
        return new ImportResult(sheet.rows().size(), imported, updated, errors.size(), errors);
    }

    private enum RowOutcome { INSERTED, UPDATED, SKIPPED_DUPLICATE }

    private RowOutcome upsertRow(String[] cells, Map<String, Integer> columns,
                                 Map<String, Department> departmentCache, boolean updateExisting) {
        String employeeCode = CsvSupport.require(cells, columns, "employee_code");
        String name = CsvSupport.require(cells, columns, "name");
        String email = CsvSupport.require(cells, columns, "email").toLowerCase();
        String departmentCode = CsvSupport.require(cells, columns, "department_code");
        String designation = CsvSupport.require(cells, columns, "designation");
        int maxWeeklyHours = CsvSupport.parseInt(
                CsvSupport.require(cells, columns, "max_weekly_hours"), "max_weekly_hours");
        String subjectCodes = CsvSupport.value(cells, columns, "subject_codes");

        Department department = departmentCache.computeIfAbsent(departmentCode.toUpperCase(),
                dc -> departmentRepository.findByCode(dc)
                        .orElseThrow(() -> ApiException.validation("Unknown department code '" + dc + "'.")));

        var existing = facultyRepository.findByEmployeeCode(employeeCode);
        Faculty faculty;
        boolean isNew = existing.isEmpty();
        if (isNew) {
            faculty = new Faculty();
        } else if (updateExisting) {
            faculty = existing.get();
        } else {
            return RowOutcome.SKIPPED_DUPLICATE;
        }

        if (isNew ? facultyRepository.existsByEmail(email)
                  : facultyRepository.existsByEmailAndIdNot(email, faculty.getId())) {
            throw ApiException.validation("Email '" + email + "' is already registered to another record.");
        }

        faculty.setEmployeeCode(employeeCode);
        faculty.setName(name);
        faculty.setEmail(email);
        faculty.setDepartment(department);
        faculty.setDesignation(designation);
        faculty.setMaxWeeklyHours(maxWeeklyHours);
        if (isNew) {
            faculty.setStatus(RecordStatus.ACTIVE);
        }
        Faculty saved = facultyRepository.save(faculty);

        // Only touch qualifications when the column carries a value, so a
        // template without it never wipes an existing member's subjects.
        if (subjectCodes != null) {
            replaceQualificationsByCode(saved, subjectCodes);
        }
        return isNew ? RowOutcome.INSERTED : RowOutcome.UPDATED;
    }

    private void replaceQualificationsByCode(Faculty faculty, String subjectCodes) {
        List<Long> subjectIds = new ArrayList<>();
        for (String rawCode : subjectCodes.split("[;|]")) {
            String code = rawCode.trim().toUpperCase();
            if (code.isEmpty()) {
                continue;
            }
            Subject subject = subjectRepository.findBySubjectCode(code)
                    .orElseThrow(() -> ApiException.validation("Unknown subject code '" + code + "'."));
            subjectIds.add(subject.getId());
        }
        replaceQualifications(faculty, subjectIds);
    }

    // ---------------------------------------------------------------- availability

    @Transactional(readOnly = true)
    public List<AvailabilityEntryDto> availability(Long facultyId) {
        find(facultyId);
        return availabilityRepository.findByFacultyId(facultyId).stream()
                .map(a -> new AvailabilityEntryDto(a.getDayOfWeek(), a.getTimeSlot().getId(),
                        a.getAvailability(), a.getNote()))
                .sorted(Comparator.comparing(AvailabilityEntryDto::dayOfWeek)
                        .thenComparing(AvailabilityEntryDto::timeSlotId))
                .toList();
    }

    /**
     * Replaces the whole availability grid for a faculty member. Only cells that
     * differ from the neutral AVAILABLE default are stored.
     */
    @Transactional
    public List<AvailabilityEntryDto> replaceAvailability(Long facultyId, List<AvailabilityEntryDto> entries) {
        Faculty faculty = find(facultyId);
        availabilityRepository.deleteByFacultyId(facultyId);
        availabilityRepository.flush();

        for (AvailabilityEntryDto entry : entries) {
            if (entry.availability() == AvailabilityType.AVAILABLE) {
                continue;
            }
            TimeSlot slot = timeSlotRepository.findById(entry.timeSlotId())
                    .orElseThrow(() -> ApiException.notFound("Time slot", entry.timeSlotId()));

            FacultyAvailability record = new FacultyAvailability();
            record.setFaculty(faculty);
            record.setDayOfWeek(entry.dayOfWeek());
            record.setTimeSlot(slot);
            record.setAvailability(entry.availability());
            record.setNote(entry.note());
            availabilityRepository.save(record);
        }

        auditService.record("UPDATE_AVAILABILITY", "Faculty", facultyId, null,
                entries.size() + " cell(s) submitted");
        return availability(facultyId);
    }

    // ---------------------------------------------------------------- leave

    @Transactional(readOnly = true)
    public List<LeaveResponse> leaves(Long facultyId) {
        return leaveRepository.findByFacultyIdOrderByStartDateDesc(facultyId).stream()
                .map(LeaveResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> allLeaves(LeaveStatus status) {
        var leaves = status == null
                ? leaveRepository.findAll()
                : leaveRepository.findByStatusOrderByStartDateAsc(status);
        return leaves.stream()
                .sorted(Comparator.comparing(FacultyLeave::getStartDate).reversed())
                .map(LeaveResponse::from).toList();
    }

    @Transactional
    public LeaveResponse submitLeave(LeaveRequest request) {
        Long facultyId = request.facultyId() != null ? request.facultyId() : resolveOwnFacultyId();
        Faculty faculty = find(facultyId);

        if (request.endDate().isBefore(request.startDate())) {
            throw ApiException.validation("The end date cannot be before the start date.");
        }

        FacultyLeave leave = new FacultyLeave();
        leave.setFaculty(faculty);
        leave.setStartDate(request.startDate());
        leave.setEndDate(request.endDate());
        leave.setLeaveType(request.leaveType() == null ? "CASUAL" : request.leaveType());
        leave.setReason(request.reason());
        leave.setStatus(LeaveStatus.PENDING);
        leave.setAppliedAt(Instant.now());

        FacultyLeave saved = leaveRepository.save(leave);
        auditService.record("SUBMIT_LEAVE", "FacultyLeave", saved.getId(), null,
                faculty.getName() + " " + request.startDate() + " to " + request.endDate());
        return LeaveResponse.from(saved);
    }

    /**
     * Approving a leave only changes the leave record. Practicals that fall
     * inside the window are surfaced by the conflict engine and moved through
     * the rescheduling workflow, never silently.
     */
    @Transactional
    public LeaveResponse decideLeave(Long leaveId, boolean approve) {
        FacultyLeave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> ApiException.notFound("Leave request", leaveId));

        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE,
                    "This leave request has already been " + leave.getStatus().name().toLowerCase() + ".");
        }

        leave.setStatus(approve ? LeaveStatus.APPROVED : LeaveStatus.REJECTED);
        leave.setReviewedAt(Instant.now());
        currentUser.userId().flatMap(userRepository::findById).ifPresent(leave::setReviewedBy);

        auditService.record(approve ? "APPROVE_LEAVE" : "REJECT_LEAVE", "FacultyLeave", leaveId);
        return LeaveResponse.from(leave);
    }

    // ---------------------------------------------------------------- helpers

    private Long resolveOwnFacultyId() {
        Long userId = currentUser.requirePrincipal().getId();
        return facultyRepository.findByUserId(userId)
                .map(Faculty::getId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_DENIED,
                        "This account is not linked to a faculty record."));
    }

    private void replaceQualifications(Faculty faculty, List<Long> subjectIds) {
        facultySubjectRepository.deleteByFacultyId(faculty.getId());
        facultySubjectRepository.flush();
        if (subjectIds == null) {
            return;
        }
        subjectIds.stream().distinct().forEach(subjectId -> {
            Subject subject = subjectRepository.findById(subjectId)
                    .orElseThrow(() -> ApiException.notFound("Subject", subjectId));
            FacultySubject link = new FacultySubject();
            link.setFaculty(faculty);
            link.setSubject(subject);
            link.setProficiency(Proficiency.PRIMARY);
            facultySubjectRepository.save(link);
        });
    }

    private List<IdNameResponse> subjectsOf(Long facultyId) {
        return facultySubjectRepository.findByFacultyId(facultyId).stream()
                .map(FacultySubject::getSubject)
                .sorted(Comparator.comparing(Subject::getSubjectName))
                .map(s -> new IdNameResponse(s.getId(), s.getSubjectCode(), s.getSubjectName()))
                .toList();
    }

    private void apply(Faculty faculty, FacultyRequest request) {
        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> ApiException.notFound("Department", request.departmentId()));
        faculty.setEmployeeCode(request.employeeCode().trim());
        faculty.setName(request.name().trim());
        faculty.setEmail(request.email().trim().toLowerCase());
        faculty.setDepartment(department);
        faculty.setDesignation(request.designation().trim());
        faculty.setMaxWeeklyHours(request.maxWeeklyHours());
        faculty.setStatus(request.status() == null ? RecordStatus.ACTIVE : request.status());
    }

    private Faculty find(Long id) {
        return facultyRepository.findById(id).orElseThrow(() -> ApiException.notFound("Faculty", id));
    }

    private static String describe(Faculty faculty) {
        return faculty.getEmployeeCode() + " | " + faculty.getName() + " | " + faculty.getDesignation()
                + " | max " + faculty.getMaxWeeklyHours() + "h | " + faculty.getStatus();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
