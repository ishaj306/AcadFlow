package edu.batchmaker.dto.faculty;

import edu.batchmaker.domain.entity.FacultyLeave;
import edu.batchmaker.domain.enums.LeaveStatus;
import java.time.Instant;
import java.time.LocalDate;

public record LeaveResponse(
        Long id,
        Long facultyId,
        String facultyName,
        String employeeCode,
        LocalDate startDate,
        LocalDate endDate,
        String leaveType,
        String reason,
        LeaveStatus status,
        Instant appliedAt,
        Instant reviewedAt,
        String reviewedBy) {

    public static LeaveResponse from(FacultyLeave leave) {
        return new LeaveResponse(
                leave.getId(),
                leave.getFaculty().getId(),
                leave.getFaculty().getName(),
                leave.getFaculty().getEmployeeCode(),
                leave.getStartDate(),
                leave.getEndDate(),
                leave.getLeaveType(),
                leave.getReason(),
                leave.getStatus(),
                leave.getAppliedAt(),
                leave.getReviewedAt(),
                leave.getReviewedBy() == null ? null : leave.getReviewedBy().getFullName());
    }
}
