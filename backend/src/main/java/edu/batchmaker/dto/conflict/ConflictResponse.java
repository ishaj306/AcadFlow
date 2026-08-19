package edu.batchmaker.dto.conflict;

import edu.batchmaker.domain.entity.Conflict;
import edu.batchmaker.domain.enums.ConflictStatus;
import edu.batchmaker.domain.enums.ConflictType;
import edu.batchmaker.domain.enums.Severity;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record ConflictResponse(
        Long id,
        Long timetableId,
        ConflictType conflictType,
        String conflictLabel,
        Severity severity,
        String description,
        String suggestedResolution,
        Long facultyId,
        String facultyName,
        Long batchId,
        String batchName,
        Long labId,
        String labName,
        Long subjectId,
        String subjectName,
        Long entryId,
        Long otherEntryId,
        DayOfWeek dayOfWeek,
        LocalDate conflictDate,
        LocalTime startTime,
        LocalTime endTime,
        ConflictStatus status,
        Instant detectedAt,
        Instant resolvedAt) {

    public static ConflictResponse from(Conflict conflict) {
        return new ConflictResponse(
                conflict.getId(),
                conflict.getTimetable() == null ? null : conflict.getTimetable().getId(),
                conflict.getConflictType(),
                conflict.getConflictType().getLabel(),
                conflict.getSeverity(),
                conflict.getDescription(),
                conflict.getSuggestedResolution(),
                conflict.getFaculty() == null ? null : conflict.getFaculty().getId(),
                conflict.getFaculty() == null ? null : conflict.getFaculty().getName(),
                conflict.getBatch() == null ? null : conflict.getBatch().getId(),
                conflict.getBatch() == null ? null : conflict.getBatch().getBatchName(),
                conflict.getLab() == null ? null : conflict.getLab().getId(),
                conflict.getLab() == null ? null : conflict.getLab().getLabName(),
                conflict.getSubject() == null ? null : conflict.getSubject().getId(),
                conflict.getSubject() == null ? null : conflict.getSubject().getSubjectName(),
                conflict.getEntryId(),
                conflict.getOtherEntryId(),
                conflict.getDayOfWeek(),
                conflict.getConflictDate(),
                conflict.getStartTime(),
                conflict.getEndTime(),
                conflict.getStatus(),
                conflict.getDetectedAt(),
                conflict.getResolvedAt());
    }
}
