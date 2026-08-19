package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.LabAvailability;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LabAvailabilityRepository extends JpaRepository<LabAvailability, Long> {

    List<LabAvailability> findByLabId(Long labId);

    void deleteByLabId(Long labId);

    @Query("""
            select la.lab.id, la.dayOfWeek, la.timeSlot.id, la.availability
            from LabAvailability la
            where la.availability <> edu.batchmaker.domain.enums.AvailabilityType.AVAILABLE
            """)
    List<Object[]> findAllNonNeutral();
}
