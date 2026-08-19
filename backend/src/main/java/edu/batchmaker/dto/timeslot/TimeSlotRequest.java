package edu.batchmaker.dto.timeslot;

import edu.batchmaker.domain.enums.SlotType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record TimeSlotRequest(
        @NotBlank(message = "Label is required")
        @Size(max = 32)
        String label,

        @NotNull(message = "Start time is required")
        LocalTime startTime,

        @NotNull(message = "End time is required")
        LocalTime endTime,

        @NotNull(message = "Slot order is required")
        Integer slotOrder,

        @NotNull(message = "Slot type is required")
        SlotType slotType,

        Boolean active) {
}
