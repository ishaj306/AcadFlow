package edu.batchmaker.dto.timetable;

import jakarta.validation.constraints.NotNull;

/**
 * A manual create/edit of one timetable session. The batch and subject are taken
 * from the chosen practical, so the caller only picks the practical, the people
 * and the place.
 */
public record EntryEditRequest(
        @NotNull Long practicalId,
        @NotNull Long facultyId,
        @NotNull Long labId,
        @NotNull String dayOfWeek,
        @NotNull Long startSlotId,
        @NotNull Long endSlotId) {
}
