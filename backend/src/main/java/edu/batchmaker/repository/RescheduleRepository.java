package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.Reschedule;
import edu.batchmaker.domain.enums.RescheduleStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RescheduleRepository extends JpaRepository<Reschedule, Long> {

    List<Reschedule> findByStatusOrderByCreatedAtDesc(RescheduleStatus status);

    List<Reschedule> findAllByOrderByCreatedAtDesc();

    List<Reschedule> findByTimetableEntryIdOrderByCreatedAtDesc(Long entryId);

    long countByStatus(RescheduleStatus status);

    /** Detail view with all relations needed by the rescheduling screen. */
    @Query("""
            select r from Reschedule r
            join fetch r.timetableEntry e
            join fetch e.subject
            join fetch e.batch
            join fetch r.originalFaculty
            join fetch r.originalLab
            where r.id = :id
            """)
    Optional<Reschedule> findDetailedById(@Param("id") Long id);

    @Query("select r.reason, count(r) from Reschedule r group by r.reason order by count(r) desc")
    List<Object[]> countGroupedByReason();
}
