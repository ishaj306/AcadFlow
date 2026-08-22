package edu.batchmaker.dto.commitment;

import edu.batchmaker.domain.entity.FixedCommitment;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record FixedCommitmentResponse(
        Long id,
        String title,
        String commitmentType,
        Long facultyId,
        String facultyName,
        Long labId,
        String labName,
        Long academicTermId,
        DayOfWeek dayOfWeek,
        Long startSlotId,
        Long endSlotId,
        LocalTime startTime,
        LocalTime endTime,
        int durationMinutes,
        String note) {

    public static FixedCommitmentResponse from(FixedCommitment c) {
        LocalTime start = c.getStartTimeSlot().getStartTime();
        LocalTime end = c.getEndTimeSlot().getEndTime();
        return new FixedCommitmentResponse(
                c.getId(),
                c.getTitle(),
                c.getCommitmentType(),
                c.getFaculty() == null ? null : c.getFaculty().getId(),
                c.getFaculty() == null ? null : c.getFaculty().getName(),
                c.getLab() == null ? null : c.getLab().getId(),
                c.getLab() == null ? null : c.getLab().getLabName(),
                c.getAcademicTerm() == null ? null : c.getAcademicTerm().getId(),
                c.getDayOfWeek(),
                c.getStartTimeSlot().getId(),
                c.getEndTimeSlot().getId(),
                start,
                end,
                (int) java.time.Duration.between(start, end).toMinutes(),
                c.getNote());
    }
}
