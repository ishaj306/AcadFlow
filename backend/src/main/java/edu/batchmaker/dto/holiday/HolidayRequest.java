package edu.batchmaker.dto.holiday;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record HolidayRequest(
        @NotNull(message = "Date is required")
        LocalDate holidayDate,

        @NotBlank(message = "Name is required")
        @Size(max = 128)
        String name,

        @Size(max = 512)
        String description,

        /** Null applies the holiday college-wide. */
        Long departmentId) {
}
