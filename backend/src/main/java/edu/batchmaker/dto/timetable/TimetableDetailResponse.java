package edu.batchmaker.dto.timetable;

import edu.batchmaker.dto.timeslot.TimeSlotResponse;
import java.time.DayOfWeek;
import java.util.List;

/** Everything the weekly grid needs in one call. */
public record TimetableDetailResponse(
        TimetableSummaryResponse timetable,
        List<DayOfWeek> days,
        List<TimeSlotResponse> timeSlots,
        List<TimetableEntryResponse> entries,
        ValidationSummary validation) {
}
