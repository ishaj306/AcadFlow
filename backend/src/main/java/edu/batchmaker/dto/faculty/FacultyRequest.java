package edu.batchmaker.dto.faculty;

import edu.batchmaker.domain.enums.RecordStatus;
import jakarta.validation.constraints.*;
import java.util.List;

public record FacultyRequest(
        @NotBlank(message = "Employee code is required")
        @Size(max = 32)
        String employeeCode,

        @NotBlank(message = "Name is required")
        @Size(max = 128)
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 160)
        String email,

        @NotNull(message = "Department is required")
        Long departmentId,

        @NotBlank(message = "Designation is required")
        @Size(max = 64)
        String designation,

        @NotNull(message = "Maximum weekly hours is required")
        @Min(value = 1, message = "Maximum weekly hours must be greater than 0")
        @Max(value = 60, message = "Maximum weekly hours must be 60 or less")
        Integer maxWeeklyHours,

        RecordStatus status,

        /** Subjects this faculty member is qualified to teach. */
        List<Long> subjectIds) {
}
