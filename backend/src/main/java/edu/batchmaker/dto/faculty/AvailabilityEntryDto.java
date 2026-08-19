package edu.batchmaker.dto.faculty;

import edu.batchmaker.domain.enums.AvailabilityType;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;

/** One (day, slot) availability cell for a faculty member or a laboratory. */
public record AvailabilityEntryDto(
        @NotNull(message = "Day is required")
        DayOfWeek dayOfWeek,

        @NotNull(message = "Time slot is required")
        Long timeSlotId,

        @NotNull(message = "Availability is required")
        AvailabilityType availability,

        String note) {
}
