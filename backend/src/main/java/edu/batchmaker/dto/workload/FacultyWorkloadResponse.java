package edu.batchmaker.dto.workload;

import java.util.List;

public record FacultyWorkloadResponse(
        Long facultyId,
        String employeeCode,
        String facultyName,
        String designation,
        String departmentCode,
        int assignedMinutes,
        double assignedHours,
        int maxWeeklyHours,
        double utilizationPercent,
        int practicalCount,
        int freeTeachingSlots,
        /** Underutilised, Balanced, Near limit or Overloaded. */
        String status,
        List<String> subjects) {
}
