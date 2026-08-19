package edu.batchmaker.dto.timetable;

import edu.batchmaker.domain.entity.Timetable;
import edu.batchmaker.domain.enums.TimetableStatus;
import java.time.Instant;

public record TimetableSummaryResponse(
        Long id,
        String name,
        Long academicTermId,
        String academicTermLabel,
        Long departmentId,
        String departmentName,
        TimetableStatus status,
        Double score,
        Integer hardViolations,
        String solverEngine,
        String solverStatus,
        Long solverRuntimeMs,
        long entryCount,
        Instant generatedAt,
        String generatedBy,
        Instant approvedAt,
        String approvedBy,
        Instant publishedAt,
        String notes) {

    public static TimetableSummaryResponse from(Timetable timetable, long entryCount) {
        return new TimetableSummaryResponse(
                timetable.getId(),
                timetable.getName(),
                timetable.getAcademicTerm().getId(),
                timetable.getAcademicTerm().getLabel(),
                timetable.getDepartment() == null ? null : timetable.getDepartment().getId(),
                timetable.getDepartment() == null ? null : timetable.getDepartment().getName(),
                timetable.getStatus(),
                timetable.getScore(),
                timetable.getHardViolations(),
                timetable.getSolverEngine(),
                timetable.getSolverStatus(),
                timetable.getSolverRuntimeMs(),
                entryCount,
                timetable.getGeneratedAt(),
                timetable.getGeneratedBy() == null ? null : timetable.getGeneratedBy().getFullName(),
                timetable.getApprovedAt(),
                timetable.getApprovedBy() == null ? null : timetable.getApprovedBy().getFullName(),
                timetable.getPublishedAt(),
                timetable.getNotes());
    }
}
