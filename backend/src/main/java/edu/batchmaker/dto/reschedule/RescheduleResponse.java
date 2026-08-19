package edu.batchmaker.dto.reschedule;

import edu.batchmaker.domain.entity.Reschedule;
import edu.batchmaker.domain.enums.RescheduleReason;
import edu.batchmaker.domain.enums.RescheduleStatus;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record RescheduleResponse(
        Long id,
        Long timetableEntryId,
        Long timetableId,
        RescheduleReason reason,
        String reasonLabel,
        String reasonDetail,
        LocalDate affectedDate,
        String subjectName,
        String subjectCode,
        String batchName,
        String division,
        DayOfWeek originalDay,
        LocalTime originalStart,
        LocalTime originalEnd,
        String originalFacultyName,
        String originalLabName,
        DayOfWeek newDay,
        LocalTime newStart,
        LocalTime newEnd,
        String newFacultyName,
        String newLabName,
        Double candidateScore,
        RescheduleStatus status,
        String initiatedBy,
        String approvedBy,
        Instant createdAt,
        Instant approvedAt,
        Instant appliedAt,
        List<RescheduleCandidateResponse> candidates) {

    public static RescheduleResponse from(Reschedule reschedule, List<RescheduleCandidateResponse> candidates) {
        var entry = reschedule.getTimetableEntry();
        return new RescheduleResponse(
                reschedule.getId(),
                entry.getId(),
                reschedule.getTimetable().getId(),
                reschedule.getReason(),
                reschedule.getReason().getLabel(),
                reschedule.getReasonDetail(),
                reschedule.getAffectedDate(),
                entry.getSubject().getSubjectName(),
                entry.getSubject().getSubjectCode(),
                entry.getBatch().getBatchName(),
                entry.getBatch().getDivision(),
                reschedule.getOriginalDay(),
                reschedule.getOriginalStart(),
                reschedule.getOriginalEnd(),
                reschedule.getOriginalFaculty().getName(),
                reschedule.getOriginalLab().getLabName(),
                reschedule.getNewDay(),
                reschedule.getNewStart(),
                reschedule.getNewEnd(),
                reschedule.getNewFaculty() == null ? null : reschedule.getNewFaculty().getName(),
                reschedule.getNewLab() == null ? null : reschedule.getNewLab().getLabName(),
                reschedule.getCandidateScore(),
                reschedule.getStatus(),
                reschedule.getInitiatedBy() == null ? null : reschedule.getInitiatedBy().getFullName(),
                reschedule.getApprovedBy() == null ? null : reschedule.getApprovedBy().getFullName(),
                reschedule.getCreatedAt(),
                reschedule.getApprovedAt(),
                reschedule.getAppliedAt(),
                candidates);
    }
}
