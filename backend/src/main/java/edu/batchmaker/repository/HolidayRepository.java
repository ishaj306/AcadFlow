package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.Holiday;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    List<Holiday> findByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate from, LocalDate to);

    List<Holiday> findByHolidayDate(LocalDate date);

    List<Holiday> findAllByOrderByHolidayDateAsc();

    boolean existsByHolidayDateAndDepartmentIsNull(LocalDate date);
}
