package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.FacultyAvailability;
import edu.batchmaker.domain.enums.AvailabilityType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FacultyAvailabilityRepository extends JpaRepository<FacultyAvailability, Long> {

    List<FacultyAvailability> findByFacultyId(Long facultyId);

    void deleteByFacultyId(Long facultyId);

    /**
     * Flat projection [facultyId, dayOfWeek, timeSlotId, availability] used to
     * build the solver payload without loading entity graphs.
     */
    @Query("""
            select fa.faculty.id, fa.dayOfWeek, fa.timeSlot.id, fa.availability
            from FacultyAvailability fa
            where fa.availability <> edu.batchmaker.domain.enums.AvailabilityType.AVAILABLE
            """)
    List<Object[]> findAllNonNeutral();

    List<FacultyAvailability> findByFacultyIdAndAvailability(Long facultyId, AvailabilityType availability);
}
