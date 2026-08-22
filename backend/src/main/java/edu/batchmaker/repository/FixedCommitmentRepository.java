package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.FixedCommitment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FixedCommitmentRepository extends JpaRepository<FixedCommitment, Long> {

    @Query("""
            select fc from FixedCommitment fc
            left join fetch fc.faculty
            left join fetch fc.lab
            left join fetch fc.startTimeSlot
            left join fetch fc.endTimeSlot
            order by fc.dayOfWeek, fc.startTimeSlot.slotOrder
            """)
    List<FixedCommitment> findAllDetailed();

    /**
     * Commitments in force for a term: those explicitly tied to it, plus the
     * term-agnostic ones (null term). Slots and targets are fetched for the
     * assembler and conflict checks.
     */
    @Query("""
            select fc from FixedCommitment fc
            left join fetch fc.faculty
            left join fetch fc.lab
            left join fetch fc.startTimeSlot
            left join fetch fc.endTimeSlot
            where fc.academicTerm is null or fc.academicTerm.id = :termId
            """)
    List<FixedCommitment> findForTerm(Long termId);
}
