package edu.batchmaker.service;

import edu.batchmaker.domain.entity.Department;
import edu.batchmaker.domain.entity.Subject;
import edu.batchmaker.domain.enums.RecordStatus;
import edu.batchmaker.domain.enums.SubjectType;
import edu.batchmaker.dto.common.ImportResult;
import edu.batchmaker.dto.subject.SubjectRequest;
import edu.batchmaker.dto.subject.SubjectResponse;
import edu.batchmaker.exception.ApiException;
import edu.batchmaker.repository.DepartmentRepository;
import edu.batchmaker.repository.FacultySubjectRepository;
import edu.batchmaker.repository.StudentBatchRepository;
import edu.batchmaker.repository.SubjectRepository;
import edu.batchmaker.service.support.CsvSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final FacultySubjectRepository facultySubjectRepository;
    private final StudentBatchRepository batchRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<SubjectResponse> list(Long departmentId, Integer semester, boolean practicalOnly) {
        List<Subject> subjects = practicalOnly
                ? subjectRepository.findSchedulablePracticalSubjects()
                : subjectRepository.findAll();

        return subjects.stream()
                .filter(s -> departmentId == null || s.getDepartment().getId().equals(departmentId))
                .filter(s -> semester == null || s.getSemester().equals(semester))
                .sorted((a, b) -> a.getSubjectName().compareToIgnoreCase(b.getSubjectName()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubjectResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public List<String> labTypes() {
        return subjectRepository.findDistinctLabTypes();
    }

    @Transactional
    public SubjectResponse create(SubjectRequest request) {
        if (subjectRepository.existsBySubjectCode(request.subjectCode())) {
            throw ApiException.duplicate("Subject code '" + request.subjectCode() + "' already exists.");
        }
        Subject subject = new Subject();
        apply(subject, request);
        Subject saved = subjectRepository.save(subject);
        auditService.record("CREATE", "Subject", saved.getId(), null, describe(saved));
        return toResponse(saved);
    }

    @Transactional
    public SubjectResponse update(Long id, SubjectRequest request) {
        Subject subject = find(id);
        String before = describe(subject);
        if (subjectRepository.existsBySubjectCodeAndIdNot(request.subjectCode(), id)) {
            throw ApiException.duplicate("Subject code '" + request.subjectCode() + "' belongs to another subject.");
        }
        apply(subject, request);
        auditService.record("UPDATE", "Subject", id, before, describe(subject));
        return toResponse(subject);
    }

    /**
     * Refuses to delete a subject that already has generated batches, since
     * that would orphan timetable history. Deactivate instead.
     */
    @Transactional
    public void delete(Long id) {
        Subject subject = find(id);
        if (!batchRepository.findBySubjectIdOrderByBatchNameAsc(id).isEmpty()) {
            throw ApiException.validation(
                    "This subject already has practical batches. Set its status to INACTIVE instead of deleting it.");
        }
        auditService.record("DELETE", "Subject", id, describe(subject), null);
        subjectRepository.delete(subject);
    }

    private static final List<String> REQUIRED_CSV_HEADERS = List.of(
            "subject_code", "subject_name", "department_code", "semester", "subject_type",
            "practical_duration_min", "sessions_per_week", "students_per_batch", "required_lab_type");

    /**
     * Bulk CSV upload of subjects. Rows are validated independently; a bad row is
     * reported and skipped rather than aborting the whole import. An existing
     * subject code is updated only when {@code updateExisting} is set, otherwise
     * it is reported as skipped so nothing is silently overwritten.
     */
    @Transactional
    public ImportResult importCsv(MultipartFile file, boolean updateExisting) {
        CsvSupport.Sheet sheet = CsvSupport.read(file, REQUIRED_CSV_HEADERS);
        Map<String, Department> departmentCache = new HashMap<>();
        List<ImportResult.RowError> errors = new ArrayList<>();
        int imported = 0;
        int updated = 0;

        for (CsvSupport.Row row : sheet.rows()) {
            String code = CsvSupport.value(row.cells(), sheet.columns(), "subject_code");
            try {
                switch (upsertRow(row.cells(), sheet.columns(), departmentCache, updateExisting)) {
                    case INSERTED -> imported++;
                    case UPDATED -> updated++;
                    case SKIPPED_DUPLICATE -> errors.add(new ImportResult.RowError(row.number(), code,
                            "Subject code already exists. Enable 'update existing' to overwrite."));
                }
            } catch (ApiException ex) {
                errors.add(new ImportResult.RowError(row.number(), code, ex.getMessage()));
            } catch (RuntimeException ex) {
                errors.add(new ImportResult.RowError(row.number(), code,
                        "Row could not be processed: " + ex.getMessage()));
            }
        }

        auditService.record("IMPORT", "Subject", null, null,
                "imported=" + imported + " updated=" + updated + " errors=" + errors.size());
        return new ImportResult(sheet.rows().size(), imported, updated, errors.size(), errors);
    }

    private enum RowOutcome { INSERTED, UPDATED, SKIPPED_DUPLICATE }

    private RowOutcome upsertRow(String[] cells, Map<String, Integer> columns,
                                 Map<String, Department> departmentCache, boolean updateExisting) {
        String code = CsvSupport.require(cells, columns, "subject_code").toUpperCase();
        String name = CsvSupport.require(cells, columns, "subject_name");
        String departmentCode = CsvSupport.require(cells, columns, "department_code");
        int semester = CsvSupport.parseInt(CsvSupport.require(cells, columns, "semester"), "semester");
        SubjectType type = parseType(CsvSupport.require(cells, columns, "subject_type"));
        int duration = CsvSupport.parseInt(CsvSupport.require(cells, columns, "practical_duration_min"),
                "practical_duration_min");
        int sessions = CsvSupport.parseInt(CsvSupport.require(cells, columns, "sessions_per_week"),
                "sessions_per_week");
        int batchSize = CsvSupport.parseInt(CsvSupport.require(cells, columns, "students_per_batch"),
                "students_per_batch");
        String labType = CsvSupport.require(cells, columns, "required_lab_type");

        Department department = departmentCache.computeIfAbsent(departmentCode.toUpperCase(),
                dc -> departmentRepository.findByCode(dc)
                        .orElseThrow(() -> ApiException.validation("Unknown department code '" + dc + "'.")));

        var existing = subjectRepository.findBySubjectCode(code);
        Subject subject;
        boolean isNew = existing.isEmpty();
        if (isNew) {
            subject = new Subject();
        } else if (updateExisting) {
            subject = existing.get();
        } else {
            return RowOutcome.SKIPPED_DUPLICATE;
        }

        subject.setSubjectCode(code);
        subject.setSubjectName(name);
        subject.setDepartment(department);
        subject.setSemester(semester);
        subject.setSubjectType(type);
        subject.setPracticalDurationMin(duration);
        subject.setSessionsPerWeek(sessions);
        subject.setStudentsPerBatch(batchSize);
        subject.setRequiredLabType(labType);
        if (isNew) {
            subject.setStatus(RecordStatus.ACTIVE);
        }
        subjectRepository.save(subject);
        return isNew ? RowOutcome.INSERTED : RowOutcome.UPDATED;
    }

    private static SubjectType parseType(String raw) {
        try {
            return SubjectType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.validation(
                    "Column 'subject_type' must be PRACTICAL, THEORY or BOTH, got '" + raw + "'.");
        }
    }

    private SubjectResponse toResponse(Subject subject) {
        int qualified = facultySubjectRepository.findQualifiedFacultyIds(subject.getId()).size();
        return SubjectResponse.from(subject, qualified);
    }

    private void apply(Subject subject, SubjectRequest request) {
        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> ApiException.notFound("Department", request.departmentId()));
        subject.setSubjectCode(request.subjectCode().trim().toUpperCase());
        subject.setSubjectName(request.subjectName().trim());
        subject.setDepartment(department);
        subject.setSemester(request.semester());
        subject.setSubjectType(request.subjectType());
        subject.setPracticalDurationMin(request.practicalDurationMin());
        subject.setSessionsPerWeek(request.sessionsPerWeek());
        subject.setStudentsPerBatch(request.studentsPerBatch());
        subject.setRequiredLabType(request.requiredLabType().trim());
        subject.setStatus(request.status() == null ? RecordStatus.ACTIVE : request.status());
    }

    private Subject find(Long id) {
        return subjectRepository.findById(id).orElseThrow(() -> ApiException.notFound("Subject", id));
    }

    private static String describe(Subject subject) {
        return subject.getSubjectCode() + " | " + subject.getSubjectName() + " | "
                + subject.getPracticalDurationMin() + "min x" + subject.getSessionsPerWeek()
                + " | batch " + subject.getStudentsPerBatch() + " | lab " + subject.getRequiredLabType();
    }
}
