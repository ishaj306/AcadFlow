package edu.batchmaker.dto.batch;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BatchGenerationRequest(
        @NotNull(message = "Department is required")
        Long departmentId,

        @NotNull(message = "Semester is required")
        @Min(value = 1, message = "Semester must be 1 or greater")
        Integer semester,

        /** Null or empty generates batches for every practical subject of the semester. */
        List<Long> subjectIds,

        /** Null or empty generates batches for every division found. */
        List<String> divisions,

        /**
         * Optional hard ceiling on batch size. When null the effective capacity is
         * min(subject batch size, largest matching laboratory).
         */
        @Min(value = 1, message = "Maximum batch size must be greater than 0")
        Integer maxBatchSize,

        /** When true, existing batches for the same subject+division are replaced. */
        boolean regenerate) {
}
