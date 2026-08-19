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

    long countByBatchId(Long batchId);

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
