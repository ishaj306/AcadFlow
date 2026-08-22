package edu.batchmaker.dto.commitment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Create a fixed weekly commitment. At least one of {@code facultyId} or
 * {@code labId} must be supplied; {@code academicTermId} may be null to make the
 * commitment apply in every term.
 */
public record FixedCommitmentRequest(
        @NotBlank(message = "A title is required") String title,
        String commitmentType,
        Long facultyId,
        Long labId,
        Long departmentId,
        Long academicTermId,
        @NotBlank(message = "A day of week is required") String dayOfWeek,
        @NotNull(message = "A start period is required") Long startSlotId,
        @NotNull(message = "An end period is required") Long endSlotId,
        String note) {
}
