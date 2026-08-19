package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.RescheduleCandidate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RescheduleCandidateRepository extends JpaRepository<RescheduleCandidate, Long> {

    @Query("""
            select c from RescheduleCandidate c
            join fetch c.faculty
            join fetch c.lab
            join fetch c.startTimeSlot
            join fetch c.endTimeSlot
            where c.reschedule.id = :rescheduleId
            order by c.rankOrder
            """)
    List<RescheduleCandidate> findDetailedByRescheduleId(Long rescheduleId);

    void deleteByRescheduleId(Long rescheduleId);
}
