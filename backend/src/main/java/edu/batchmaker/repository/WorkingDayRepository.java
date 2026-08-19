package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.WorkingDay;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkingDayRepository extends JpaRepository<WorkingDay, Long> {

    List<WorkingDay> findByActiveTrueOrderByDayOrderAsc();

    List<WorkingDay> findAllByOrderByDayOrderAsc();

    Optional<WorkingDay> findByDayOfWeek(DayOfWeek dayOfWeek);
}
