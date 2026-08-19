package edu.batchmaker.dto.batch;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Manual override after automatic generation (spec section 12). The supplied
 * assignments completely replace the membership of the listed batches.
 */
public record BatchAdjustmentRequest(
        @NotEmpty(message = "At least one batch assignment is required")
        List<BatchAssignment> assignments) {

    public record BatchAssignment(Long batchId, List<Long> studentIds) {
    }
}
