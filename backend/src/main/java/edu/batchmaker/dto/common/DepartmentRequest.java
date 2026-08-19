package edu.batchmaker.dto.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DepartmentRequest(
        @NotBlank(message = "Department code is required")
        @Size(max = 16, message = "Department code must be at most 16 characters")
        String code,

        @NotBlank(message = "Department name is required")
        @Size(max = 128)
        String name) {
}
