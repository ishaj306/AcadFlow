package edu.batchmaker.service;

import edu.batchmaker.domain.entity.*;
import edu.batchmaker.domain.enums.LabStatus;
import edu.batchmaker.domain.enums.RecordStatus;
import edu.batchmaker.dto.batch.*;
import edu.batchmaker.exception.ApiException;
import edu.batchmaker.exception.ErrorCode;
import edu.batchmaker.repository.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Practical batch generator (spec section 12).
 *
 * <p>Batch count is {@code ceil(students / effectiveCapacity)} and the cohort is
 * then split as evenly as possible, so 127 students at a capacity of 32 become
 * 32/32/32/31 rather than 32/32/32/31 with a stray remainder batch. Effective
 * capacity is the smaller of the subject's configured batch size and the largest
 * laboratory of the required type, so a batch can never be created that no lab
 * can physically host (hard constraint H4).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchGenerationService {

    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;
    private final LaboratoryRepository labRepository;
    private final StudentBatchRepository batchRepository;
    private final BatchStudentRepository batchStudentRepository;
    private final PracticalRepository practicalRepository;
    private final DepartmentRepository departmentRepository;
    private final MasterDataService masterDataService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<BatchResponse> list(Long subjectId, String division) {
        AcademicTerm term = masterDataService.requireCurrentTerm();
        List<StudentBatch> batches = subjectId != null
                ? batchRepository.findBySubjectIdOrderByBatchNameAsc(subjectId)
                : batchRepository.findActiveForTermWithSubject(term.getId());

        return batches.stream()
                .filter(b -> division == null || b.getDivision().equalsIgnoreCase(division))
                .map(b -> BatchResponse.from(b, membersOf(b.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public BatchResponse get(Long batchId) {
        StudentBatch batch = findBatch(batchId);
        return BatchResponse.from(batch, membersOf(batchId));
    }

    @Transactional
    public BatchGenerationResult generate(BatchGenerationRequest request) {
        AcademicTerm term = masterDataService.requireCurrentTerm();
        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> ApiException.notFound("Department", request.departmentId()));

        List<Subject> subjects = resolveSubjects(request);
        if (subjects.isEmpty()) {
            throw new ApiException(ErrorCode.BATCH_GENERATION_FAILED,
                    "No practical subjects match the selected department and semester.");
        }

        List<String> divisions = resolveDivisions(request);
        if (divisions.isEmpty()) {
            throw new ApiException(ErrorCode.BATCH_GENERATION_FAILED,
                    "No active students found for the selected department and semester.");
        }

        List<String> warnings = new ArrayList<>();
        List<BatchResponse> created = new ArrayList<>();
        int replaced = 0;
        int studentsAssigned = 0;

        for (Subject subject : subjects) {
            int labCeiling = largestLabCapacity(subject.getRequiredLabType());
            if (labCeiling == 0) {
                warnings.add("No active laboratory of type '" + subject.getRequiredLabType()
                        + "' exists, so batches for " + subject.getSubjectName()
                        + " were sized on the subject limit alone and may not be schedulable.");
            }

            int effectiveCapacity = effectiveCapacity(subject, labCeiling, request.maxBatchSize());

            for (String division : divisions) {
                List<Student> students = studentRepository
                        .findByDepartmentIdAndSemesterAndDivisionAndStatusOrderByRollNumberAsc(
                                department.getId(), request.semester(), division, RecordStatus.ACTIVE);

                if (students.isEmpty()) {
                    continue;
                }

                List<StudentBatch> existing = batchRepository.findExisting(
                        subject.getId(), division, term.getId());
                if (!existing.isEmpty()) {
                    if (!request.regenerate()) {
                        warnings.add("Batches already exist for " + subject.getSubjectCode()
                                + " division " + division + "; enable 'regenerate' to replace them.");
                        continue;
                    }
                    replaced += existing.size();
                    deleteBatches(existing);
                }

                List<StudentBatch> batches = splitIntoBatches(
                        subject, department, term, division, request.semester(), students, effectiveCapacity);

                studentsAssigned += students.size();
                batches.forEach(b -> created.add(BatchResponse.from(b, membersOf(b.getId()))));
            }
        }

        auditService.record("GENERATE", "StudentBatch", null, null,
                "created=" + created.size() + " replaced=" + replaced + " students=" + studentsAssigned);

        return new BatchGenerationResult(subjects.size(), created.size(), replaced,
                studentsAssigned, warnings, created);
    }

    /**
     * Splits a division into evenly sized batches and creates the matching
     * {@link Practical} demand rows the optimizer will schedule.
     */
    private List<StudentBatch> splitIntoBatches(Subject subject, Department department, AcademicTerm term,
                                                String division, Integer semester, List<Student> students,
                                                int effectiveCapacity) {
        int total = students.size();
        int batchCount = (int) Math.ceil((double) total / effectiveCapacity);
        int base = total / batchCount;
        int remainder = total % batchCount;

        List<StudentBatch> result = new ArrayList<>();
        int cursor = 0;

        for (int i = 0; i < batchCount; i++) {
            // The first `remainder` batches absorb the extra student each.
            int size = base + (i < remainder ? 1 : 0);
            String label = batchLabel(i);

            StudentBatch batch = new StudentBatch();
            batch.setBatchCode(subject.getSubjectCode() + "-" + division + "-" + label + "-"
                    + term.getAcademicYear().replace("-", "") + "S" + term.getSemester());
            batch.setBatchName("Batch " + label);
            batch.setDepartment(department);
            batch.setSubject(subject);
            batch.setAcademicTerm(term);
            batch.setDivision(division);
            batch.setSemester(semester);
            batch.setCapacity(effectiveCapacity);
            batch.setStudentCount(size);
            batch.setRequiredLabType(subject.getRequiredLabType());
            batch.setStatus(RecordStatus.ACTIVE);
            StudentBatch saved = batchRepository.save(batch);

            for (int j = 0; j < size; j++) {
                BatchStudent membership = new BatchStudent();
                membership.setBatch(saved);
                membership.setStudent(students.get(cursor++));
                batchStudentRepository.save(membership);
            }

            Practical practical = new Practical();
            practical.setSubject(subject);
            practical.setBatch(saved);
            practical.setAcademicTerm(term);
            practical.setSessionsPerWeek(subject.getSessionsPerWeek());
            practical.setDurationMin(subject.getPracticalDurationMin());
            practical.setStatus(RecordStatus.ACTIVE);
            practicalRepository.save(practical);

            result.add(saved);
        }
        return result;
    }

    /** Manual post-generation adjustment of batch membership. */
    @Transactional
    public List<BatchResponse> adjust(BatchAdjustmentRequest request) {
        List<BatchResponse> updated = new ArrayList<>();

        for (var assignment : request.assignments()) {
            StudentBatch batch = findBatch(assignment.batchId());
            List<Long> studentIds = assignment.studentIds() == null ? List.of() : assignment.studentIds();

            if (studentIds.size() > batch.getCapacity()) {
                throw new ApiException(ErrorCode.CAPACITY_EXCEEDED,
                        batch.getBatchName() + " would hold " + studentIds.size()
                                + " students but its capacity is " + batch.getCapacity() + ".");
            }

            batchStudentRepository.deleteByBatchId(batch.getId());
            batchStudentRepository.flush();

            Long subjectId = batch.getSubject().getId();
            for (Long studentId : studentIds.stream().distinct().toList()) {
                Student student = studentRepository.findById(studentId)
                        .orElseThrow(() -> ApiException.notFound("Student", studentId));
                // A student may sit in only one batch per subject; otherwise the
                // same person owes the practical twice and can never be free of a
                // clash. The clearing delete above is already committed, so the
                // check runs against the other batches of this subject.
                if (batchStudentRepository.existsInAnotherBatchForSubject(studentId, subjectId, batch.getId())) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED,
                            student.getName() + " is already in another batch for "
                                    + batch.getSubject().getSubjectName() + ".");
                }
                BatchStudent membership = new BatchStudent();
                membership.setBatch(batch);
                membership.setStudent(student);
                batchStudentRepository.save(membership);
            }

            batch.setStudentCount(studentIds.size());
            updated.add(BatchResponse.from(batch, membersOf(batch.getId())));
        }

        auditService.record("ADJUST", "StudentBatch", null, null,
                request.assignments().size() + " batch(es) adjusted manually");
        return updated;
    }

    /**
     * Swaps two students between their batches for one subject. Both students
     * must already be enrolled in a (different) batch of that subject; the
     * exchange is one-for-one, so neither batch changes size.
     */
    @Transactional
    public List<BatchResponse> swap(edu.batchmaker.dto.batch.BatchSwapRequest request) {
        if (request.studentAId().equals(request.studentBId())) {
            throw ApiException.validation("Choose two different students to swap.");
        }

        BatchStudent membershipA = membershipForSubject(request.studentAId(), request.subjectId());
        BatchStudent membershipB = membershipForSubject(request.studentBId(), request.subjectId());

        StudentBatch batchA = membershipA.getBatch();
        StudentBatch batchB = membershipB.getBatch();
        if (batchA.getId().equals(batchB.getId())) {
            throw ApiException.validation("Both students are already in the same batch, so there is nothing to swap.");
        }

        // One-for-one exchange keeps each batch's size unchanged.
        membershipA.setBatch(batchB);
        membershipB.setBatch(batchA);
        batchStudentRepository.save(membershipA);
        batchStudentRepository.save(membershipB);

        auditService.record("SWAP", "StudentBatch", null, null,
                "students " + request.studentAId() + "<->" + request.studentBId()
                        + " between " + batchA.getBatchName() + " and " + batchB.getBatchName());

        return List.of(
                BatchResponse.from(batchA, membersOf(batchA.getId())),
                BatchResponse.from(batchB, membersOf(batchB.getId())));
    }

    private BatchStudent membershipForSubject(Long studentId, Long subjectId) {
        List<BatchStudent> memberships = batchStudentRepository.findMembershipsForSubject(studentId, subjectId);
        if (memberships.isEmpty()) {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> ApiException.notFound("Student", studentId));
            throw ApiException.validation(student.getName() + " is not enrolled in any batch for this subject.");
        }
        return memberships.get(0);
    }

    @Transactional
    public void deleteBatchesForSubject(Long subjectId) {
        List<StudentBatch> batches = batchRepository.findBySubjectIdOrderByBatchNameAsc(subjectId);
        deleteBatches(batches);
        auditService.record("DELETE", "StudentBatch", null, null,
                batches.size() + " batch(es) removed for subject " + subjectId);
    }

    private void deleteBatches(List<StudentBatch> batches) {
        if (batches.isEmpty()) {
            return;
        }
        List<Long> ids = batches.stream().map(StudentBatch::getId).toList();
        practicalRepository.deleteByBatchIdIn(ids);
        batchStudentRepository.deleteByBatchIdIn(ids);
        batchRepository.deleteAll(batches);
        batchRepository.flush();
    }

    private int effectiveCapacity(Subject subject, int labCeiling, Integer requestedMax) {
        int capacity = subject.getStudentsPerBatch();
        if (labCeiling > 0) {
            capacity = Math.min(capacity, labCeiling);
        }
        if (requestedMax != null) {
            capacity = Math.min(capacity, requestedMax);
        }
        if (capacity < 1) {
            throw new ApiException(ErrorCode.BATCH_GENERATION_FAILED,
                    "Effective batch capacity for " + subject.getSubjectName() + " resolved to zero.");
        }
        return capacity;
    }

    private int largestLabCapacity(String labType) {
        return labRepository.findByLabTypeAndStatus(labType, LabStatus.ACTIVE).stream()
                .mapToInt(Laboratory::getCapacity)
                .max()
                .orElse(0);
    }

    private List<Subject> resolveSubjects(BatchGenerationRequest request) {
        if (request.subjectIds() != null && !request.subjectIds().isEmpty()) {
            return request.subjectIds().stream()
                    .map(id -> subjectRepository.findById(id)
                            .orElseThrow(() -> ApiException.notFound("Subject", id)))
                    .filter(s -> s.getSubjectType().hasPractical())
                    .toList();
        }
        return subjectRepository.findSchedulablePracticalSubjects().stream()
                .filter(s -> s.getDepartment().getId().equals(request.departmentId()))
                .filter(s -> s.getSemester().equals(request.semester()))
                .toList();
    }

    private List<String> resolveDivisions(BatchGenerationRequest request) {
        if (request.divisions() != null && !request.divisions().isEmpty()) {
            return request.divisions().stream().map(String::toUpperCase).toList();
        }
        return studentRepository.findDivisions(request.departmentId(), request.semester());
    }

    /** A, B, ... Z, then AA, AB, ... for very large cohorts. */
    private static String batchLabel(int index) {
        StringBuilder label = new StringBuilder();
        int value = index;
        do {
            label.insert(0, (char) ('A' + value % 26));
            value = value / 26 - 1;
        } while (value >= 0);
        return label.toString();
    }

    private List<BatchResponse.BatchMember> membersOf(Long batchId) {
        return batchStudentRepository.findByBatchIdWithStudent(batchId).stream()
                .map(BatchStudent::getStudent)
                .sorted(Comparator.comparing(Student::getRollNumber))
                .map(s -> new BatchResponse.BatchMember(s.getId(), s.getRollNumber(), s.getName()))
                .toList();
    }

    private StudentBatch findBatch(Long id) {
        return batchRepository.findById(id).orElseThrow(() -> ApiException.notFound("Batch", id));
    }
}
