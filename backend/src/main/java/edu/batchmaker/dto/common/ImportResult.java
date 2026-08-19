package edu.batchmaker.dto.common;

import java.util.List;

/**
 * Outcome of a CSV bulk upload: what was written and exactly what was rejected.
 * Generic across master-data entities — the per-row {@code reference} carries
 * whatever natural key identifies the row (employee code, subject code, …) so a
 * user can find and fix the offending line.
 */
public record ImportResult(
        int totalRows,
        int imported,
        int updated,
        int skipped,
        List<RowError> errors) {

    public record RowError(int rowNumber, String reference, String message) {
    }
}
