package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.StudentBatch;
import edu.batchmaker.domain.enums.RecordStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StudentBatchRepository extends JpaRepository<StudentBatch, Long> {

    List<StudentBatch> findBySubjectIdOrderByBatchNameAsc(Long subjectId);

    List<StudentBatch> findBySubjectIdAndDivision(Long subjectId, String division);

    List<StudentBatch> findByAcademicTermIdAndStatus(Long academicTermId, RecordStatus status);

    long countByStatus(RecordStatus status);

    boolean existsByBatchCode(String batchCode);

    @Query("""
            select b from StudentBatch b
            join fetch b.subject s
            join fetch b.department d
            where b.academicTerm.id = :termId
              and b.status = edu.batchmaker.domain.enums.RecordStatus.ACTIVE
            order by s.subjectName, b.division, b.batchName
            """)
    List<StudentBatch> findActiveForTermWithSubject(Long termId);

    @Query("""
            select b from StudentBatch b
            where b.subject.id = :subjectId and b.division = :division
              and b.academicTerm.id = :termId
            """)
    List<StudentBatch> findExisting(Long subjectId, String division, Long termId);
}
