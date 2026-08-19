package edu.batchmaker.dto.timeslot;

import edu.batchmaker.domain.entity.WorkingDay;
import java.time.DayOfWeek;

public record WorkingDayDto(Long id, DayOfWeek dayOfWeek, boolean active, Integer dayOrder) {

    public static WorkingDayDto from(WorkingDay day) {
        return new WorkingDayDto(day.getId(), day.getDayOfWeek(), day.isActive(), day.getDayOrder());
    }
}
