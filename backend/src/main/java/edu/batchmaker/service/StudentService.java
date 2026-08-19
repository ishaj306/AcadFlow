package edu.batchmaker.service;

import edu.batchmaker.domain.entity.Department;
import edu.batchmaker.domain.entity.Student;
import edu.batchmaker.domain.enums.RecordStatus;
import edu.batchmaker.dto.common.PageResponse;
import edu.batchmaker.dto.student.StudentImportResult;
import edu.batchmaker.dto.student.StudentRequest;
import edu.batchmaker.dto.student.StudentResponse;
import edu.batchmaker.exception.ApiException;
import edu.batchmaker.repository.DepartmentRepository;
import edu.batchmaker.repository.StudentRepository;
import edu.batchmaker.service.support.CsvSupport;
import java.util.ArrayList;
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
public class StudentService {

    private static final List<String> REQUIRED_CSV_HEADERS =
            List.of("roll_number", "name", "email", "department_code", "semester", "year", "division");

    private final StudentRepository studentRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<StudentResponse> search(String search, Long departmentId, Integer semester,
                                                String division, RecordStatus status, Pageable pageable) {
        return PageResponse.from(
                studentRepository.search(blankToNull(search), departmentId, semester,
                        blankToNull(division), status, pageable),
                StudentResponse::from);
    }

    @Transactional(readOnly = true)
    public StudentResponse get(Long id) {
        return StudentResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public List<String> divisions(Long departmentId, Integer semester) {
        return studentRepository.findDivisions(departmentId, semester);
    }

    @Transactional
    public StudentResponse create(StudentRequest request) {
        if (studentRepository.existsByRollNumber(request.rollNumber())) {
            throw ApiException.duplicate("Roll number '" + request.rollNumber() + "' already exists.");
        }
        if (studentRepository.existsByEmail(request.email())) {
            throw ApiException.duplicate("Email '" + request.email() + "' is already registered.");
        }

        Student student = new Student();
        apply(student, request);
        Student saved = studentRepository.save(student);
        auditService.record("CREATE", "Student", saved.getId(), null, describe(saved));
        return StudentResponse.from(saved);
    }

    @Transactional
    public StudentResponse update(Long id, StudentRequest request) {
        Student student = find(id);
        String before = describe(student);

        if (studentRepository.existsByRollNumberAndIdNot(request.rollNumber(), id)) {
            throw ApiException.duplicate("Roll number '" + request.rollNumber() + "' belongs to another student.");
        }
        if (studentRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw ApiException.duplicate("Email '" + request.email() + "' belongs to another student.");
        }

        apply(student, request);
        auditService.record("UPDATE", "Student", id, before, describe(student));
        return StudentResponse.from(student);
    }

    /** Deactivates rather than deletes, so historical batches stay intact. */
    @Transactional
    public void deactivate(Long id) {
        Student student = find(id);
        student.setStatus(RecordStatus.INACTIVE);
        auditService.record("DEACTIVATE", "Student", id);
    }

    @Transactional
    public void delete(Long id) {
        Student student = find(id);
        auditService.record("DELETE", "Student", id, describe(student), null);
        studentRepository.delete(student);
    }

    /**
     * Bulk upload from CSV or {@code .xlsx}. Rows are validated independently; a
     * bad row is reported and skipped rather than aborting the whole import.
     */
    @Transactional
    public StudentImportResult importCsv(MultipartFile file, boolean updateExisting) {
        CsvSupport.Sheet sheet = CsvSupport.read(file, REQUIRED_CSV_HEADERS);
        List<StudentImportResult.RowError> errors = new ArrayList<>();
        Map<String, Department> departmentCache = new HashMap<>();
        int imported = 0;
        int updated = 0;

        for (CsvSupport.Row row : sheet.rows()) {
            String rollNumber = CsvSupport.value(row.cells(), sheet.columns(), "roll_number");
            try {
                switch (upsertRow(row.cells(), sheet.columns(), departmentCache, updateExisting)) {
                    case INSERTED -> imported++;
                    case UPDATED -> updated++;
                    case SKIPPED_DUPLICATE -> errors.add(new StudentImportResult.RowError(row.number(), rollNumber,
                            "Roll number already exists. Enable 'update existing' to overwrite."));
                }
            } catch (ApiException ex) {
                errors.add(new StudentImportResult.RowError(row.number(), rollNumber, ex.getMessage()));
            } catch (RuntimeException ex) {
                errors.add(new StudentImportResult.RowError(row.number(), rollNumber,
                        "Row could not be processed: " + ex.getMessage()));
            }
        }

        auditService.record("IMPORT", "Student", null, null,
                "imported=" + imported + " updated=" + updated + " errors=" + errors.size());

        return new StudentImportResult(sheet.rows().size(), imported, updated, errors.size(), errors);
    }

    private enum RowOutcome { INSERTED, UPDATED, SKIPPED_DUPLICATE }

    private RowOutcome upsertRow(String[] cells, Map<String, Integer> columns,
                                 Map<String, Department> departmentCache, boolean updateExisting) {
        String rollNumber = CsvSupport.require(cells, columns, "roll_number");
        String name = CsvSupport.require(cells, columns, "name");
        String email = CsvSupport.require(cells, columns, "email");
        String departmentCode = CsvSupport.require(cells, columns, "department_code");
        int semester = CsvSupport.parseInt(CsvSupport.require(cells, columns, "semester"), "semester");
        int year = CsvSupport.parseInt(CsvSupport.require(cells, columns, "year"), "year");
        String division = CsvSupport.require(cells, columns, "division");

        Department department = departmentCache.computeIfAbsent(departmentCode.toUpperCase(),
                code -> departmentRepository.findByCode(code)
                        .orElseThrow(() -> ApiException.validation("Unknown department code '" + code + "'.")));

        var existing = studentRepository.findByRollNumber(rollNumber);
        if (existing.isPresent()) {
            if (!updateExisting) {
                return RowOutcome.SKIPPED_DUPLICATE;
            }
            Student student = existing.get();
            if (studentRepository.existsByEmailAndIdNot(email, student.getId())) {
                throw ApiException.validation("Email '" + email + "' is already registered to another student.");
            }
            student.setName(name);
            student.setEmail(email);
            student.setDepartment(department);
            student.setSemester(semester);
            student.setStudyYear(year);
            student.setDivision(division);
            return RowOutcome.UPDATED;
        }

        if (studentRepository.existsByEmail(email)) {
            throw ApiException.validation("Email '" + email + "' is already registered to another student.");
        }

        Student student = new Student();
        student.setRollNumber(rollNumber);
        student.setName(name);
        student.setEmail(email);
        student.setDepartment(department);
        student.setSemester(semester);
        student.setStudyYear(year);
        student.setDivision(division);
        student.setStatus(RecordStatus.ACTIVE);
        studentRepository.save(student);
        return RowOutcome.INSERTED;
    }

    private void apply(Student student, StudentRequest request) {
        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> ApiException.notFound("Department", request.departmentId()));
        student.setRollNumber(request.rollNumber().trim());
        student.setName(request.name().trim());
        student.setEmail(request.email().trim().toLowerCase());
        student.setDepartment(department);
        student.setSemester(request.semester());
        student.setStudyYear(request.studyYear());
        student.setDivision(request.division().trim().toUpperCase());
        student.setStatus(request.status() == null ? RecordStatus.ACTIVE : request.status());
    }

    private Student find(Long id) {
        return studentRepository.findById(id).orElseThrow(() -> ApiException.notFound("Student", id));
    }

    private static String describe(Student student) {
        return student.getRollNumber() + " | " + student.getName() + " | sem " + student.getSemester()
                + " | div " + student.getDivision() + " | " + student.getStatus();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
