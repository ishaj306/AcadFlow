package edu.batchmaker.dto.batch;

import jakarta.validation.constraints.NotNull;

/**
 * Swaps two students between their batches for one subject.
 *
 * <p>Both students consent out of band; an administrator then applies the swap.
 * Because it is a one-for-one exchange, each batch keeps its size, and the
 * "one batch per subject per student" invariant is preserved.
 */
public record BatchSwapRequest(
        @NotNull(message = "The subject is required") Long subjectId,
        @NotNull(message = "The first student is required") Long studentAId,
        @NotNull(message = "The second student is required") Long studentBId) {
}
