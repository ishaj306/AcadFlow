package edu.batchmaker.dto.timetable;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record TimetableGenerateRequest(
        @Size(max = 128)
        String name,

        Long departmentId,

        /** Solver time budget. Longer runs give the optimizer more room. */
        @Min(value = 1, message = "The time limit must be at least 1 second")
        @Max(value = 600, message = "The time limit must be 600 seconds or less")
        Integer maxSeconds,

        /** Optional soft-constraint weight overrides from the tuning sliders; null uses defaults. */
        WeightOverrides weights) {

    /**
     * Objective weights sent straight from the UI, in camelCase (unlike the
     * solver's snake_case contract). Any null field falls back to the default.
     */
    public record WeightOverrides(
            Double workloadImbalance,
            Double facultyPreference,
            Double studentGaps,
            Double labUnderutilization,
            Double scheduleChanges,
            Double facultyIdle) {
    }
}
