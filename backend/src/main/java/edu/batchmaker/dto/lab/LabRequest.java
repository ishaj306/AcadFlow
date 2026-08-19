package edu.batchmaker.dto.lab;

import edu.batchmaker.domain.enums.LabStatus;
import jakarta.validation.constraints.*;

public record LabRequest(
        @NotBlank(message = "Lab code is required")
        @Size(max = 32)
        String labCode,

        @NotBlank(message = "Lab name is required")
        @Size(max = 128)
        String labName,

        @NotNull(message = "Department is required")
        Long departmentId,

        @NotNull(message = "Capacity is required")
        @Min(value = 1, message = "Capacity must be greater than 0")
        @Max(value = 500, message = "Capacity must be 500 or less")
        Integer capacity,

        @NotBlank(message = "Lab type is required")
        @Size(max = 48)
        String labType,

        @Size(max = 128)
        String location,

        LabStatus status) {
}
