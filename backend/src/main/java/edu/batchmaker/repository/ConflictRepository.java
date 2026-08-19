package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.Conflict;
import edu.batchmaker.domain.enums.ConflictStatus;
import edu.batchmaker.domain.enums.Severity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConflictRepository extends JpaRepository<Conflict, Long> {

    List<Conflict> findByStatusOrderBySeverityAscDetectedAtDesc(ConflictStatus status);

    List<Conflict> findByTimetableId(Long timetableId);

    List<Conflict> findByTimetableIdAndStatus(Long timetableId, ConflictStatus status);

    long countByStatus(ConflictStatus status);

    long countByStatusAndSeverity(ConflictStatus status, Severity severity);

    void deleteByTimetableId(Long timetableId);

    @Query("""
            select c.conflictType, count(c) from Conflict c
            group by c.conflictType order by count(c) desc
            """)
    List<Object[]> countGroupedByType();
}
