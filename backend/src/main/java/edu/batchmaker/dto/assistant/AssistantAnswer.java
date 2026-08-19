package edu.batchmaker.dto.assistant;

import edu.batchmaker.dto.timetable.TimetableEntryResponse;
import java.util.List;

/**
 * The assistant's reply: a plain-language answer, any matching sessions to show
 * as a list, and a few example questions to guide the next query.
 */
public record AssistantAnswer(
        String answer,
        List<TimetableEntryResponse> entries,
        List<String> suggestions) {
}
