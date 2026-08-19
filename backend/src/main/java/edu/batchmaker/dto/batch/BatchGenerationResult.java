package edu.batchmaker.dto.batch;

import java.util.List;

/** Summary returned after generating practical batches. */
public record BatchGenerationResult(
        int subjectsProcessed,
        int batchesCreated,
        int batchesReplaced,
        int studentsAssigned,
        List<String> warnings,
        List<BatchResponse> batches) {
}
