package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.FacultyLeave;
import edu.batchmaker.domain.enums.LeaveStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FacultyLeaveRepository extends JpaRepository<FacultyLeave, Long> {

    List<FacultyLeave> findByFacultyIdOrderByStartDateDesc(Long facultyId);

    List<FacultyLeave> findByStatusOrderByStartDateAsc(LeaveStatus status);

    long countByStatus(LeaveStatus status);

    /** Approved leaves overlapping a date window - drives hard constraint H9. */
    @Query("""
            select l from FacultyLeave l
            where l.status = edu.batchmaker.domain.enums.LeaveStatus.APPROVED
              and l.startDate <= :to and l.endDate >= :from
            """)
    List<FacultyLeave> findApprovedOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select count(l) > 0 from FacultyLeave l
            where l.faculty.id = :facultyId
              and l.status = edu.batchmaker.domain.enums.LeaveStatus.APPROVED
              and l.startDate <= :date and l.endDate >= :date
            """)
    boolean isOnApprovedLeave(@Param("facultyId") Long facultyId, @Param("date") LocalDate date);
}
