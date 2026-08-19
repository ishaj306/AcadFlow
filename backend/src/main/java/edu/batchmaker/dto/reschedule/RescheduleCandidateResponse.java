package edu.batchmaker.dto.reschedule;

import edu.batchmaker.domain.entity.RescheduleCandidate;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record RescheduleCandidateResponse(
        Long id,
        Integer rank,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Long startSlotId,
        Long endSlotId,
        Long facultyId,
        String facultyName,
        Long labId,
        String labName,
        String labLocation,
        double score,
        String scoreBreakdown,
        boolean selected) {

    public static RescheduleCandidateResponse from(RescheduleCandidate candidate) {
        return new RescheduleCandidateResponse(
                candidate.getId(),
                candidate.getRankOrder(),
                candidate.getDayOfWeek(),
                candidate.getStartTime(),
                candidate.getEndTime(),
                candidate.getStartTimeSlot().getId(),
                candidate.getEndTimeSlot().getId(),
                candidate.getFaculty().getId(),
                candidate.getFaculty().getName(),
                candidate.getLab().getId(),
                candidate.getLab().getLabName(),
                candidate.getLab().getLocation(),
                candidate.getScore(),
                candidate.getScoreBreakdown(),
                candidate.isSelected());
    }
}
