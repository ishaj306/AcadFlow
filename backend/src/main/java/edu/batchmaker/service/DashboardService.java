package edu.batchmaker.service;

import edu.batchmaker.domain.entity.Laboratory;
import edu.batchmaker.domain.entity.Timetable;
import edu.batchmaker.domain.entity.TimetableEntry;
import edu.batchmaker.domain.enums.*;
import edu.batchmaker.dto.conflict.ConflictResponse;
import edu.batchmaker.dto.dashboard.DashboardResponse;
import edu.batchmaker.dto.timetable.TimetableEntryResponse;
import edu.batchmaker.repository.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final StudentRepository studentRepository;
    private final FacultyRepository facultyRepository;
    private final LaboratoryRepository labRepository;
    private final StudentBatchRepository batchRepository;
    private final TimetableRepository timetableRepository;
    private final TimetableEntryRepository entryRepository;
    private final ConflictRepository conflictRepository;
    private final RescheduleRepository rescheduleRepository;
    private final FacultyLeaveRepository leaveRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final WorkingDayRepository workingDayRepository;
    private final AcademicTermRepository termRepository;
    private final WorkloadService workloadService;

    @Transactional(readOnly = true)
    public DashboardResponse load() {
        LocalDate today = LocalDate.now();
        DayOfWeek dayOfWeek = today.getDayOfWeek();

        Optional<Timetable> published = timetableRepository
                .findFirstByStatusOrderByPublishedAtDesc(TimetableStatus.PUBLISHED);

        List<TimetableEntry> entries = published
                .map(t -> entryRepository.findDetailedByTimetableId(t.getId()))
                .orElse(List.of());

        List<TimetableEntryResponse> todays = entries.stream()
                .filter(e -> e.getDayOfWeek() == dayOfWeek)
                .sorted(Comparator.comparing(TimetableEntry::getStartTime))
                .map(TimetableEntryResponse::from)
                .toList();

        // The next two working days after today, so the card is useful on any day.
        List<DayOfWeek> workingDays = workingDayRepository.findByActiveTrueOrderByDayOrderAsc().stream()
                .map(d -> d.getDayOfWeek()).toList();
        List<DayOfWeek> upcomingDays = nextDays(workingDays, dayOfWeek, 2);

        List<TimetableEntryResponse> upcoming = entries.stream()
                .filter(e -> upcomingDays.contains(e.getDayOfWeek()))
                .sorted(Comparator.comparing((TimetableEntry e) -> upcomingDays.indexOf(e.getDayOfWeek()))
                        .thenComparing(TimetableEntry::getStartTime))
                .limit(12)
                .map(TimetableEntryResponse::from)
                .toList();

        List<ConflictResponse> openConflicts = conflictRepository
                .findByStatusOrderBySeverityAscDetectedAtDesc(ConflictStatus.OPEN).stream()
                .limit(10)
                .map(ConflictResponse::from)
                .toList();

        var workload = published.isPresent()
                ? workloadService.summary(published.get().getId(), null)
                : null;

        List<DashboardResponse.WorkloadBar> workloadBars = workload == null ? List.of()
                : workload.faculty().stream()
                        .sorted(Comparator.comparingDouble(f -> -f.utilizationPercent()))
                        .map(f -> new DashboardResponse.WorkloadBar(
                                f.facultyName(), f.employeeCode(), f.assignedHours(),
                                f.maxWeeklyHours(), f.utilizationPercent(), f.status()))
                        .toList();

        List<DashboardResponse.UtilizationBar> labBars = labUtilization(entries);

        double overallLabUtilization = labBars.isEmpty() ? 0.0
                : Math.round(labBars.stream().mapToDouble(DashboardResponse.UtilizationBar::utilizationPercent)
                        .average().orElse(0) * 10) / 10.0;

        var counters = new DashboardResponse.Counters(
                studentRepository.countByStatus(RecordStatus.ACTIVE),
                facultyRepository.countByStatus(RecordStatus.ACTIVE),
                labRepository.countByStatus(LabStatus.ACTIVE),
                batchRepository.countByStatus(RecordStatus.ACTIVE),
                todays.size(),
                conflictRepository.countByStatus(ConflictStatus.OPEN),
                workload == null ? 0 : workload.overloadedCount(),
                rescheduleRepository.countByStatus(RescheduleStatus.PROPOSED),
                leaveRepository.countByStatus(LeaveStatus.PENDING),
                overallLabUtilization,
                published.map(t -> t.getScore() == null ? null : (int) Math.round(t.getScore())).orElse(null));

        return new DashboardResponse(
                counters,
                workloadBars,
                labBars,
                todays,
                upcoming,
                openConflicts,
                termRepository.findByCurrentTrue().map(t -> t.getLabel()).orElse("No current term"),
                published.map(Timetable::getName).orElse(null),
                today,
                dayOfWeek);
    }

    /** Percentage of each lab's weekly teaching periods that are booked. */
    private List<DashboardResponse.UtilizationBar> labUtilization(List<TimetableEntry> entries) {
        int teachingSlots = timeSlotRepository.findSchedulableSlots().size();
        int days = workingDayRepository.findByActiveTrueOrderByDayOrderAsc().size();
        int slotsPerWeek = Math.max(1, teachingSlots * days);

        Map<Long, List<TimetableEntry>> byLab = entries.stream()
                .collect(Collectors.groupingBy(e -> e.getLab().getId()));

        return labRepository.findByStatusOrderByLabNameAsc(LabStatus.ACTIVE).stream()
                .map(lab -> {
                    List<TimetableEntry> labEntries = byLab.getOrDefault(lab.getId(), List.of());
                    long usedMinutes = labEntries.stream()
                            .mapToLong(e -> java.time.Duration.between(
                                    e.getStartTime(), e.getEndTime()).toMinutes())
                            .sum();
                    double percent = Math.round(usedMinutes * 1000.0 / (slotsPerWeek * 60.0)) / 10.0;
                    return new DashboardResponse.UtilizationBar(
                            lab.getLabName(), lab.getLabCode(), percent,
                            labEntries.size(), lab.getCapacity());
                })
                .sorted(Comparator.comparingDouble(DashboardResponse.UtilizationBar::utilizationPercent).reversed())
                .toList();
    }

    private static List<DayOfWeek> nextDays(List<DayOfWeek> workingDays, DayOfWeek from, int count) {
        if (workingDays.isEmpty()) {
            return List.of();
        }
        int start = workingDays.indexOf(from);
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(offset -> workingDays.get(((start < 0 ? 0 : start) + offset) % workingDays.size()))
                .toList();
    }

    /** Convenience accessor used by the lab utilisation report. */
    @Transactional(readOnly = true)
    public List<Laboratory> activeLabs() {
        return labRepository.findByStatusOrderByLabNameAsc(LabStatus.ACTIVE);
    }
}
