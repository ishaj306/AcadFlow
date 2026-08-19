package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.TimetableEntry;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimetableEntryRepository extends JpaRepository<TimetableEntry, Long> {

    /**
     * All entries of a timetable with every display relation pre-joined. Used by
     * the grid, the conflict detector and the exporters, so it is fetched once
     * rather than lazily per row.
     */
    @Query("""
            select e from TimetableEntry e
            join fetch e.subject
            join fetch e.faculty
            join fetch e.lab
            join fetch e.batch b
            join fetch e.startTimeSlot
            join fetch e.endTimeSlot
            where e.timetable.id = :timetableId
              and e.status <> edu.batchmaker.domain.enums.EntryStatus.CANCELLED
            order by e.dayOfWeek, e.startTime
            """)
    List<TimetableEntry> findDetailedByTimetableId(@Param("timetableId") Long timetableId);

    @Query("""
            select e from TimetableEntry e
            join fetch e.subject
            join fetch e.faculty
            join fetch e.lab
            join fetch e.batch
            join fetch e.startTimeSlot
            join fetch e.endTimeSlot
            where e.id = :id
            """)
    Optional<TimetableEntry> findDetailedById(@Param("id") Long id);

    List<TimetableEntry> findByTimetableId(Long timetableId);

    List<TimetableEntry> findByTimetableIdAndDayOfWeek(Long timetableId, DayOfWeek dayOfWeek);

    List<TimetableEntry> findByTimetableIdAndFacultyId(Long timetableId, Long facultyId);

    List<TimetableEntry> findByTimetableIdAndBatchIdIn(Long timetableId, List<Long> batchIds);

    List<TimetableEntry> findByTimetableIdAndLabId(Long timetableId, Long labId);

    void deleteByTimetableId(Long timetableId);

    long countByTimetableId(Long timetableId);

    /** Total scheduled minutes per faculty, for the workload dashboard. */
    @Query("""
            select e.faculty.id, sum(
                (hour(e.endTime) * 60 + minute(e.endTime)) -
                (hour(e.startTime) * 60 + minute(e.startTime)))
            from TimetableEntry e
            where e.timetable.id = :timetableId
              and e.status <> edu.batchmaker.domain.enums.EntryStatus.CANCELLED
            group by e.faculty.id
            """)
    List<Object[]> sumMinutesByFaculty(@Param("timetableId") Long timetableId);

    /** Total scheduled minutes per laboratory, for utilisation reporting. */
    @Query("""
            select e.lab.id, sum(
                (hour(e.endTime) * 60 + minute(e.endTime)) -
                (hour(e.startTime) * 60 + minute(e.startTime)))
            from TimetableEntry e
            where e.timetable.id = :timetableId
              and e.status <> edu.batchmaker.domain.enums.EntryStatus.CANCELLED
            group by e.lab.id
            """)
    List<Object[]> sumMinutesByLab(@Param("timetableId") Long timetableId);
}
