package edu.batchmaker.dto.timeslot;

import edu.batchmaker.domain.entity.TimeSlot;
import edu.batchmaker.domain.enums.SlotType;
import java.time.LocalTime;

public record TimeSlotResponse(
        Long id,
        String label,
        LocalTime startTime,
        LocalTime endTime,
        Integer slotOrder,
        SlotType slotType,
        boolean active,
        int durationMinutes) {

    public static TimeSlotResponse from(TimeSlot slot) {
        return new TimeSlotResponse(
                slot.getId(),
                slot.getLabel(),
                slot.getStartTime(),
                slot.getEndTime(),
                slot.getSlotOrder(),
                slot.getSlotType(),
                slot.isActive(),
                slot.getDurationMinutes());
    }
}
