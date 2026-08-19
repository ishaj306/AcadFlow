package edu.batchmaker.dto.solver;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * Wire contract with the Python CP-SAT service. Field names are snake_case to
 * match the Pydantic models on the other side; the rest of the API keeps
 * camelCase, so the mapping is declared here rather than globally.
 */
public final class SolverPayload {

    private SolverPayload() {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record TimeSlotIn(Long id, Integer order, Integer startMinute, Integer endMinute, boolean teachable) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record LabIn(Long id, Integer capacity, String labType, List<List<Object>> unavailable) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FacultyIn(Long id, Integer maxWeeklyMinutes, List<Long> qualifiedSubjectIds,
                            List<List<Object>> unavailable, List<List<Object>> preferred) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PracticalIn(Long id, Long subjectId, Long batchId, Integer studentCount,
                              String requiredLabType, Integer durationMinutes, Integer sessionsPerWeek,
                              Long preferredFacultyId) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PreviousAssignment(Long practicalId, Integer sessionIndex, String day,
                                     Long startSlotId, Long facultyId, Long labId) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Weights(Double workloadImbalance, Double facultyPreference, Double studentGaps,
                          Double labUnderutilization, Double scheduleChanges, Double facultyIdle) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Request(List<String> days,
                          List<TimeSlotIn> timeSlots,
                          List<LabIn> labs,
                          List<FacultyIn> faculty,
                          List<PracticalIn> practicals,
                          List<List<Long>> studentConflictGroups,
                          List<List<Long>> studentCohorts,
                          List<PreviousAssignment> locked,
                          List<PreviousAssignment> previous,
                          Weights weights,
                          Double maxSeconds,
                          Integer workers) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AssignmentOut(Long practicalId, Integer sessionIndex, Long batchId, Long subjectId,
                                Long facultyId, Long labId, String day, Long startSlotId, Long endSlotId,
                                String startTime, String endTime) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ViolationOut(String code, String message, Long practicalId) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ScoreBreakdown(Integer workloadImbalanceMinutes, Integer facultyPreferenceViolations,
                                 Integer studentGapSlots, Integer facultyIdleSlots, Integer labSurplusSeats,
                                 Integer scheduleChanges, Double weightedPenalty) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Response(String status, Double scheduleScore, String engine, Long runtimeMs,
                           List<AssignmentOut> assignments, List<ViolationOut> violations,
                           List<String> warnings, ScoreBreakdown breakdown) {
    }

    // ----------------------------------------------------------- feasibility

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FeasibilityBlocker(String code, String message, Long practicalId, Long subjectId) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FeasibilityCheck(String key, String label, Integer required, Integer available,
                                   String unit, boolean satisfied, Double utilizationPercent, String detail) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FeasibilitySuggestion(String code, String title, String detail, String category,
                                        Integer estimatedGainSessions) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FeasibilityBatchLoad(Long batchId, Integer sessionsRequired, String labType) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FeasibilityReport(String verdict, Long runtimeMs, Integer totalSessionsRequired,
                                    Integer totalSessionCapacity, List<FeasibilityBlocker> blockers,
                                    List<FeasibilityCheck> resourceChecks, List<FeasibilityBatchLoad> batchLoads,
                                    List<FeasibilitySuggestion> suggestions, List<String> notes) {
    }
}
