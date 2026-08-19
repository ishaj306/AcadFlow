package edu.batchmaker.dto.student;

import java.util.List;

/** Outcome of a CSV bulk upload: what was written and exactly what was rejected. */
public record StudentImportResult(
        int totalRows,
        int imported,
        int updated,
        int skipped,
        List<RowError> errors) {

    public record RowError(int rowNumber, String rollNumber, String message) {
    }
}
