package edu.batchmaker.dto.faculty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record LeaveRequest(
        Long facultyId,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate,

        @Size(max = 32)
        String leaveType,

        @Size(max = 512)
        String reason) {
}
