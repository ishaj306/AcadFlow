package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.Timetable;
import edu.batchmaker.domain.enums.TimetableStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimetableRepository extends JpaRepository<Timetable, Long> {

    List<Timetable> findAllByOrderByGeneratedAtDesc();

    List<Timetable> findByStatusOrderByGeneratedAtDesc(TimetableStatus status);

    /** The schedule currently in force. */
    Optional<Timetable> findFirstByStatusOrderByPublishedAtDesc(TimetableStatus status);

    Optional<Timetable> findFirstByAcademicTermIdAndStatusOrderByPublishedAtDesc(
            Long academicTermId, TimetableStatus status);

    long countByStatus(TimetableStatus status);
}
