package edu.batchmaker.dto.timetable;

import edu.batchmaker.domain.entity.TimetableEntry;
import edu.batchmaker.domain.enums.EntryStatus;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record TimetableEntryResponse(
        Long id,
        Long practicalId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Long startSlotId,
        Long endSlotId,
        Integer sessionIndex,
        EntryStatus status,
        Long subjectId,
        String subjectCode,
        String subjectName,
        Long batchId,
        String batchName,
        String division,
        Integer studentCount,
        Long facultyId,
        String facultyName,
        String facultyCode,
        Long labId,
        String labName,
        String labLocation,
        Integer labCapacity) {

    public static TimetableEntryResponse from(TimetableEntry entry) {
        return new TimetableEntryResponse(
                entry.getId(),
                entry.getPractical() == null ? null : entry.getPractical().getId(),
                entry.getDayOfWeek(),
                entry.getStartTime(),
                entry.getEndTime(),
                entry.getStartTimeSlot().getId(),
                entry.getEndTimeSlot().getId(),
                entry.getSessionIndex(),
                entry.getStatus(),
                entry.getSubject().getId(),
                entry.getSubject().getSubjectCode(),
                entry.getSubject().getSubjectName(),
                entry.getBatch().getId(),
                entry.getBatch().getBatchName(),
                entry.getBatch().getDivision(),
                entry.getBatch().getStudentCount(),
                entry.getFaculty().getId(),
                entry.getFaculty().getName(),
                entry.getFaculty().getEmployeeCode(),
                entry.getLab().getId(),
                entry.getLab().getLabName(),
                entry.getLab().getLocation(),
                entry.getLab().getCapacity());
    }
}
