package edu.batchmaker.dto.student;

import edu.batchmaker.domain.enums.RecordStatus;
import jakarta.validation.constraints.*;

public record StudentRequest(
        @NotBlank(message = "Roll number is required")
        @Size(max = 32, message = "Roll number must be at most 32 characters")
        String rollNumber,

        @NotBlank(message = "Name is required")
        @Size(max = 128)
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 160)
        String email,

        @NotNull(message = "Department is required")
        Long departmentId,

        @NotNull(message = "Semester is required")
        @Min(value = 1, message = "Semester must be between 1 and 12")
        @Max(value = 12, message = "Semester must be between 1 and 12")
        Integer semester,

        @NotNull(message = "Year is required")
        @Min(value = 1, message = "Year must be between 1 and 6")
        @Max(value = 6, message = "Year must be between 1 and 6")
        Integer studyYear,

        @NotBlank(message = "Division is required")
        @Size(max = 8)
        String division,

        RecordStatus status) {
}
