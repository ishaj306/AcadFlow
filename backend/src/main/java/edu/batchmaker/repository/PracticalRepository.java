package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.Practical;
import edu.batchmaker.domain.enums.RecordStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PracticalRepository extends JpaRepository<Practical, Long> {

    List<Practical> findByAcademicTermIdAndStatus(Long academicTermId, RecordStatus status);

    List<Practical> findByBatchId(Long batchId);

    void deleteByBatchIdIn(List<Long> batchIds);

    boolean existsBySubjectIdAndBatchIdAndAcademicTermId(Long subjectId, Long batchId, Long termId);

    /** Full schedulable demand for a term, eagerly loaded for solver input. */
    @Query("""
            select p from Practical p
            join fetch p.subject s
            join fetch p.batch b
            where p.academicTerm.id = :termId
              and p.status = edu.batchmaker.domain.enums.RecordStatus.ACTIVE
            order by s.subjectName, b.batchName
            """)
    List<Practical> findActiveForTerm(Long termId);
}
