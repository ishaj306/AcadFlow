package edu.batchmaker.dto.holiday;

import edu.batchmaker.domain.entity.Holiday;
import java.time.LocalDate;

public record HolidayResponse(
        Long id,
        LocalDate holidayDate,
        String name,
        String description,
        Long departmentId,
        String departmentName) {

    public static HolidayResponse from(Holiday holiday) {
        return new HolidayResponse(
                holiday.getId(),
                holiday.getHolidayDate(),
                holiday.getName(),
                holiday.getDescription(),
                holiday.getDepartment() == null ? null : holiday.getDepartment().getId(),
                holiday.getDepartment() == null ? null : holiday.getDepartment().getName());
    }
}
