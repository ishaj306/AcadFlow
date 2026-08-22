package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.BatchStudent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BatchStudentRepository extends JpaRepository<BatchStudent, Long> {

    @Query("""
            select bs from BatchStudent bs
            join fetch bs.student st
            where bs.batch.id = :batchId
            order by st.rollNumber
            """)
    List<BatchStudent> findByBatchIdWithStudent(Long batchId);

    List<BatchStudent> findByStudentId(Long studentId);

    /** The student's membership row in a batch of the given subject, if any. */
    @Query("""
            select bs from BatchStudent bs
            join fetch bs.batch b
            join fetch b.subject
            where bs.student.id = :studentId and b.subject.id = :subjectId
            """)
    List<BatchStudent> findMembershipsForSubject(Long studentId, Long subjectId);

    long countByBatchId(Long batchId);

    /**
     * True when the student already belongs to a <em>different</em> batch of the
     * same subject - used to stop a manual adjustment double-enrolling one
     * student into two batches for the same practical.
     */
    @Query("""
            select case when count(bs) > 0 then true else false end
            from BatchStudent bs
            where bs.student.id = :studentId
              and bs.batch.subject.id = :subjectId
              and bs.batch.id <> :excludeBatchId
            """)
    boolean existsInAnotherBatchForSubject(Long studentId, Long subjectId, Long excludeBatchId);

    void deleteByBatchId(Long batchId);

    void deleteByBatchIdIn(List<Long> batchIds);

    /**
     * Every batch a student belongs to, with the batch and subject loaded -
     * backs the student timetable view.
     */
    @Query("""
            select bs from BatchStudent bs
            join fetch bs.batch b
            join fetch b.subject
            where bs.student.id = :studentId
            """)
    List<BatchStudent> findByStudentIdWithBatch(Long studentId);
}
