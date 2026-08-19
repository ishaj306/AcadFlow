package edu.batchmaker.dto.workload;

import java.util.List;

public record WorkloadSummaryResponse(
        Long timetableId,
        String timetableName,
        int facultyCount,
        double averageUtilizationPercent,
        int overloadedCount,
        int nearLimitCount,
        int balancedCount,
        int underutilizedCount,
        double spreadHours,
        String balanceVerdict,
        List<FacultyWorkloadResponse> faculty) {
}
