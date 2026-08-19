package edu.batchmaker.dto.timetable;

import java.util.List;

/** Returned straight after generation, before any approval decision. */
public record GenerationResultResponse(
        String status,
        TimetableDetailResponse preview,
        List<String> solverWarnings,
        List<String> solverViolations,
        SolverMetrics metrics) {

    public record SolverMetrics(
            String engine,
            long runtimeMs,
            double rawScore,
            Integer workloadImbalanceMinutes,
            Integer facultyPreferenceViolations,
            Integer studentGapSlots,
            Integer facultyIdleSlots,
            Integer labSurplusSeats,
            Integer scheduleChanges) {
    }
}
