package edu.batchmaker.dto.common;

import java.util.List;

/**
 * Readiness of an installation, in the order things must be entered. Drives the
 * guided setup checklist shown while the database is still being populated.
 */
public record SetupStatusResponse(
        boolean ready,
        int completedSteps,
        int totalSteps,
        List<Step> steps) {

    public record Step(
            String key,
            String title,
            String description,
            String route,
            boolean complete,
            long count,
            /** Why this step is not yet satisfied, when it is not. */
            String blocker) {
    }
}
