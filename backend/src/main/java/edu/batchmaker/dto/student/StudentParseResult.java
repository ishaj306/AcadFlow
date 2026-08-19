package edu.batchmaker.dto.student;

import java.util.List;

/**
 * Preview of parsing pasted roster text: the students found, a suggested split
 * into practical batches, and any lines that could not be understood.
 */
public record StudentParseResult(
        List<ParsedStudent> students,
        List<SuggestedBatch> suggestedBatches,
        List<String> warnings) {

    public record ParsedStudent(String rollNumber, String name, String division) {
    }

    public record SuggestedBatch(String batchName, String division, int studentCount) {
    }
}
