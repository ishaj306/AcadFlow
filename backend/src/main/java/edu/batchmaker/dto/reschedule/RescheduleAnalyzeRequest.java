package edu.batchmaker.dto.reschedule;

import edu.batchmaker.domain.enums.RescheduleReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record RescheduleAnalyzeRequest(
        @NotNull(message = "The timetable entry is required")
        Long timetableEntryId,

        @NotNull(message = "A reason is required")
        RescheduleReason reason,

        @Size(max = 512)
        String reasonDetail,

        /** The specific date affected, when the trigger is date-bound. */
        LocalDate affectedDate,

        /** How many ranked alternatives to return. Defaults to 5. */
        Integer maxCandidates) {
}
