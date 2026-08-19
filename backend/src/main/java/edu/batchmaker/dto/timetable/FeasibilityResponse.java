package edu.batchmaker.dto.timetable;

import java.util.List;

/**
 * Result of the pre-generation feasibility audit, enriched with the human-readable
 * names the UI needs. The raw solver report speaks in ids; this speaks in subjects,
 * batches and divisions.
 */
public record FeasibilityResponse(
        String verdict,
        int totalSessionsRequired,
        int totalSessionCapacity,
        List<Blocker> blockers,
        List<Check> resourceChecks,
        List<BatchLoad> batchLoads,
        List<Suggestion> suggestions,
        List<String> notes,
        long runtimeMs) {

    /** A hard reason one practical cannot be placed at all. */
    public record Blocker(String code, String message, Long practicalId,
                          String subjectName, String batchName, String division) {
    }

    /** One necessary-condition test: enough of a resource for the demand on it. */
    public record Check(String key, String label, int required, int available, String unit,
                        boolean satisfied, double utilizationPercent, String detail) {
    }

    /** How many sessions a single batch needs, for the breakdown table. */
    public record BatchLoad(Long batchId, String batchName, String division,
                            int sessionsRequired, String labType) {
    }

    /** A concrete change that would relieve a shortage, with its capacity gain. */
    public record Suggestion(String code, String title, String detail, String category,
                             Integer estimatedGainSessions) {
    }
}
