package edu.batchmaker.service;

import edu.batchmaker.domain.entity.*;
import edu.batchmaker.domain.enums.RecordStatus;
import edu.batchmaker.domain.enums.TimetableStatus;
import edu.batchmaker.dto.workload.FacultyWorkloadResponse;
import edu.batchmaker.dto.workload.WorkloadSummaryResponse;
import edu.batchmaker.exception.ApiException;
import edu.batchmaker.exception.ErrorCode;
import edu.batchmaker.repository.*;
import java.time.Duration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Faculty workload dashboard (spec sections 25-26). */
@Service
@RequiredArgsConstructor
public class WorkloadService {

    private final FacultyRepository facultyRepository;
    private final FacultySubjectRepository facultySubjectRepository;
    private final TimetableRepository timetableRepository;
    private final TimetableEntryRepository entryRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final WorkingDayRepository workingDayRepository;

    @Transactional(readOnly = true)
    public WorkloadSummaryResponse summary(Long timetableId, Long departmentId) {
        Timetable timetable = timetableId != null
                ? timetableRepository.findById(timetableId)
                        .orElseThrow(() -> ApiException.notFound("Timetable", timetableId))
                : timetableRepository.findFirstByStatusOrderByPublishedAtDesc(TimetableStatus.PUBLISHED)
                        .orElseThrow(() -> new ApiException(ErrorCode.TIMETABLE_NOT_PUBLISHED,
                                "No timetable has been published yet, so there is no workload to report."));

        List<TimetableEntry> entries = entryRepository.findDetailedByTimetableId(timetable.getId());

        Map<Long, Integer> minutes = new HashMap<>();
        Map<Long, Integer> counts = new HashMap<>();
        for (TimetableEntry entry : entries) {
            Long facultyId = entry.getFaculty().getId();
            minutes.merge(facultyId, (int) Duration.between(
                    entry.getStartTime(), entry.getEndTime()).toMinutes(), Integer::sum);
            counts.merge(facultyId, 1, Integer::sum);
        }

        int totalTeachingSlots = timeSlotRepository.findSchedulableSlots().size()
                * workingDayRepository.findByActiveTrueOrderByDayOrderAsc().size();

        List<Faculty> faculty = facultyRepository.findByStatusOrderByNameAsc(RecordStatus.ACTIVE).stream()
                .filter(f -> departmentId == null || f.getDepartment().getId().equals(departmentId))
                .toList();

        List<FacultyWorkloadResponse> rows = faculty.stream().map(member -> {
            int assigned = minutes.getOrDefault(member.getId(), 0);
            int limit = member.getMaxWeeklyHours() * 60;
            double utilization = limit == 0 ? 0 : Math.round(assigned * 1000.0 / limit) / 10.0;
            int occupiedSlots = (int) Math.ceil(assigned / 60.0);

            List<String> subjects = facultySubjectRepository.findByFacultyId(member.getId()).stream()
                    .map(fs -> fs.getSubject().getSubjectName())
                    .sorted()
                    .toList();

            return new FacultyWorkloadResponse(
                    member.getId(),
                    member.getEmployeeCode(),
                    member.getName(),
                    member.getDesignation(),
                    member.getDepartment().getCode(),
                    assigned,
                    Math.round(assigned / 60.0 * 10) / 10.0,
                    member.getMaxWeeklyHours(),
                    utilization,
                    counts.getOrDefault(member.getId(), 0),
                    Math.max(0, totalTeachingSlots - occupiedSlots),
                    band(utilization),
                    subjects);
        }).toList();

        DoubleSummaryStatistics stats = rows.stream()
                .mapToDouble(FacultyWorkloadResponse::utilizationPercent)
                .summaryStatistics();

        List<Double> assignedHours = rows.stream()
                .map(FacultyWorkloadResponse::assignedHours)
                .toList();
        double spread = assignedHours.isEmpty() ? 0
                : Math.round((Collections.max(assignedHours) - Collections.min(assignedHours)) * 10) / 10.0;

        return new WorkloadSummaryResponse(
                timetable.getId(),
                timetable.getName(),
                rows.size(),
                rows.isEmpty() ? 0 : Math.round(stats.getAverage() * 10) / 10.0,
                (int) rows.stream().filter(r -> "Overloaded".equals(r.status())).count(),
                (int) rows.stream().filter(r -> "Near limit".equals(r.status())).count(),
                (int) rows.stream().filter(r -> "Balanced".equals(r.status())).count(),
                (int) rows.stream().filter(r -> "Underutilised".equals(r.status())).count(),
                spread,
                verdict(spread),
                rows);
    }

    @Transactional(readOnly = true)
    public FacultyWorkloadResponse forFaculty(Long facultyId) {
        return summary(null, null).faculty().stream()
                .filter(row -> row.facultyId().equals(facultyId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Faculty workload", facultyId));
    }

    /** Utilisation bands from spec section 25. */
    private static String band(double utilizationPercent) {
        if (utilizationPercent > 100) {
            return "Overloaded";
        }
        if (utilizationPercent >= 85) {
            return "Near limit";
        }
        if (utilizationPercent >= 50) {
            return "Balanced";
        }
        return "Underutilised";
    }

    private static String verdict(double spreadHours) {
        if (spreadHours <= 2) {
            return "Good";
        }
        if (spreadHours <= 4) {
            return "Acceptable";
        }
        return "Uneven";
    }
}
