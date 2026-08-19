package edu.batchmaker.dto.student;

import jakarta.validation.constraints.NotBlank;

/**
 * Raw roster text pasted by the user, to be parsed into student records and
 * split into practical batches. This is a <em>preview</em> only — nothing is
 * saved until the user imports it.
 */
public record StudentParseRequest(
        @NotBlank String rawText,
        Integer batchSize) {
}
