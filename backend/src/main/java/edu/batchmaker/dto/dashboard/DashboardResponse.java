package edu.batchmaker.dto.dashboard;

import edu.batchmaker.dto.conflict.ConflictResponse;
import edu.batchmaker.dto.timetable.TimetableEntryResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/** Operational snapshot for the dashboard (spec section 6). */
public record DashboardResponse(
        Counters counters,
        List<WorkloadBar> facultyWorkload,
        List<UtilizationBar> labUtilization,
        List<TimetableEntryResponse> todaysPracticals,
        List<TimetableEntryResponse> upcomingPracticals,
        List<ConflictResponse> openConflicts,
        String currentTermLabel,
        String publishedTimetableName,
        LocalDate today,
        DayOfWeek todayDayOfWeek) {

    public record Counters(
            long totalStudents,
            long totalFaculty,
            long totalLabs,
            long practicalBatches,
            long todaysPracticals,
            long activeConflicts,
            long overloadedFaculty,
            long pendingReschedules,
            long pendingLeaves,
            double labUtilizationPercent,
            Integer scheduleScore) {
    }

    public record WorkloadBar(String facultyName, String employeeCode,
                              double assignedHours, int maxHours, double utilizationPercent, String status) {
    }

    public record UtilizationBar(String labName, String labCode, double utilizationPercent,
                                 int sessionsPerWeek, int capacity) {
    }
}
