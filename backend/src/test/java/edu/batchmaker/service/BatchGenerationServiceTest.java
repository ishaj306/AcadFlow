package edu.batchmaker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.batchmaker.domain.entity.*;
import edu.batchmaker.domain.enums.LabStatus;
import edu.batchmaker.domain.enums.RecordStatus;
import edu.batchmaker.domain.enums.SubjectType;
import edu.batchmaker.dto.batch.BatchAdjustmentRequest;
import edu.batchmaker.dto.batch.BatchGenerationRequest;
import edu.batchmaker.dto.batch.BatchResponse;
import edu.batchmaker.dto.batch.BatchSwapRequest;
import edu.batchmaker.exception.ApiException;
import edu.batchmaker.repository.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the practical-batch logic: even splitting, the
 * one-for-one student swap, and the guard against double-enrolling a student in
 * two batches of the same subject. Runs against an isolated in-memory database.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:batchmaker_batch_test;DB_CLOSE_DELAY=-1",
        "batchmaker.bootstrap.admin-password=test-admin-password"
})
class BatchGenerationServiceTest {

    @Autowired private BatchGenerationService service;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private AcademicTermRepository termRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private LaboratoryRepository labRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StudentBatchRepository batchRepository;
    @Autowired private BatchStudentRepository batchStudentRepository;

    private Long departmentId;
    private Long subjectId;

    @BeforeEach
    void seed() {
        Department dept = new Department();
        dept.setCode("CSE");
        dept.setName("Computer Science");
        dept.setStatus(RecordStatus.ACTIVE);
        dept = departmentRepository.save(dept);
        departmentId = dept.getId();

        AcademicTerm term = new AcademicTerm();
        term.setAcademicYear("2026-27");
        term.setSemester(5);
        term.setStartDate(LocalDate.of(2026, 7, 20));
        term.setEndDate(LocalDate.of(2026, 11, 27));
        term.setCurrent(true);
        termRepository.save(term);

        Subject subject = new Subject();
        subject.setSubjectCode("CS501");
        subject.setSubjectName("Java Programming");
        subject.setDepartment(dept);
        subject.setSemester(5);
        subject.setSubjectType(SubjectType.PRACTICAL);
        subject.setPracticalDurationMin(120);
        subject.setSessionsPerWeek(1);
        subject.setStudentsPerBatch(30);
        subject.setRequiredLabType("Programming");
        subject.setStatus(RecordStatus.ACTIVE);
        subjectId = subjectRepository.save(subject).getId();

        Laboratory lab = new Laboratory();
        lab.setLabCode("PL1");
        lab.setLabName("Programming Lab 1");
        lab.setDepartment(dept);
        lab.setCapacity(30);
        lab.setLabType("Programming");
        lab.setStatus(LabStatus.ACTIVE);
        labRepository.save(lab);

        for (int i = 1; i <= 50; i++) {
            Student s = new Student();
            s.setRollNumber(String.format("CS5A%03d", i));
            s.setName("Student " + i);
            s.setEmail("student" + i + "@test.edu");
            s.setDepartment(dept);
            s.setSemester(5);
            s.setStudyYear(3);
            s.setDivision("A");
            s.setStatus(RecordStatus.ACTIVE);
            studentRepository.save(s);
        }
    }

    private BatchGenerationRequest generateRequest() {
        return new BatchGenerationRequest(departmentId, 5, List.of(subjectId), null, null, true);
    }

    @Test
    void generatesEvenBatchesThatFitTheLabAndAssignEveryStudentOnce() {
        service.generate(generateRequest());

        List<StudentBatch> batches = batchRepository.findBySubjectIdOrderByBatchNameAsc(subjectId);
        // 50 students, capacity 30 -> 2 batches of 25 each.
        assertEquals(2, batches.size(), "50 students at capacity 30 form two batches");

        int total = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (StudentBatch batch : batches) {
            int size = (int) batchStudentRepository.countByBatchId(batch.getId());
            total += size;
            min = Math.min(min, size);
            max = Math.max(max, size);
            assertTrue(size <= batch.getCapacity(), "no batch exceeds lab capacity");
        }
        assertEquals(50, total, "every student is assigned exactly once");
        assertTrue(max - min <= 1, "batch sizes differ by at most one");
    }

    @Test
    void swapExchangesTwoStudentsAndKeepsBatchSizes() {
        service.generate(generateRequest());
        List<StudentBatch> batches = batchRepository.findBySubjectIdOrderByBatchNameAsc(subjectId);
        StudentBatch batchA = batches.get(0);
        StudentBatch batchB = batches.get(1);

        Long studentA = batchStudentRepository.findByBatchIdWithStudent(batchA.getId())
                .get(0).getStudent().getId();
        Long studentB = batchStudentRepository.findByBatchIdWithStudent(batchB.getId())
                .get(0).getStudent().getId();
        int sizeA = (int) batchStudentRepository.countByBatchId(batchA.getId());
        int sizeB = (int) batchStudentRepository.countByBatchId(batchB.getId());

        List<BatchResponse> result = service.swap(new BatchSwapRequest(subjectId, studentA, studentB));
        assertEquals(2, result.size());

        boolean aNowInB = batchStudentRepository.findByBatchIdWithStudent(batchB.getId()).stream()
                .anyMatch(bs -> bs.getStudent().getId().equals(studentA));
        boolean bNowInA = batchStudentRepository.findByBatchIdWithStudent(batchA.getId()).stream()
                .anyMatch(bs -> bs.getStudent().getId().equals(studentB));
        assertTrue(aNowInB, "student A moved into batch B");
        assertTrue(bNowInA, "student B moved into batch A");
        assertEquals(sizeA, (int) batchStudentRepository.countByBatchId(batchA.getId()), "batch A size unchanged");
        assertEquals(sizeB, (int) batchStudentRepository.countByBatchId(batchB.getId()), "batch B size unchanged");
    }

    @Test
    void adjustRejectsDoubleEnrolmentInTheSameSubject() {
        service.generate(generateRequest());
        List<StudentBatch> batches = batchRepository.findBySubjectIdOrderByBatchNameAsc(subjectId);
        StudentBatch batchA = batches.get(0);
        StudentBatch batchB = batches.get(1);

        Long studentInA = batchStudentRepository.findByBatchIdWithStudent(batchA.getId())
                .get(0).getStudent().getId();

        // Putting a student who already sits in batch A into batch B (same subject)
        // must be rejected — otherwise they owe the practical twice.
        BatchAdjustmentRequest request = new BatchAdjustmentRequest(List.of(
                new BatchAdjustmentRequest.BatchAssignment(batchB.getId(), List.of(studentInA))));

        assertThrows(ApiException.class, () -> service.adjust(request));
    }
}
