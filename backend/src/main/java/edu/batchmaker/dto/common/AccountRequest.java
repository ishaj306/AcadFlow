package edu.batchmaker.dto.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Creates a sign-in account for an existing faculty member or student. The
 * username defaults to their employee code / roll number when left blank.
 */
public record AccountRequest(
        @Size(max = 64)
        String username,

        @NotBlank(message = "A password is required")
        @Size(min = 8, message = "The password must be at least 8 characters")
        String password) {
}
