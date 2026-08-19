package edu.batchmaker.service;

import edu.batchmaker.domain.entity.TimeSlot;
import edu.batchmaker.domain.entity.WorkingDay;
import edu.batchmaker.dto.timeslot.TimeSlotRequest;
import edu.batchmaker.dto.timeslot.TimeSlotResponse;
import edu.batchmaker.dto.timeslot.WorkingDayDto;
import edu.batchmaker.exception.ApiException;
import edu.batchmaker.repository.TimeSlotRepository;
import edu.batchmaker.repository.TimetableEntryRepository;
import edu.batchmaker.repository.WorkingDayRepository;
import java.time.DayOfWeek;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Configuration of college working hours and working days (spec section 11). */
@Service
@RequiredArgsConstructor
public class TimeSlotService {

    private final TimeSlotRepository timeSlotRepository;
    private final WorkingDayRepository workingDayRepository;
    private final TimetableEntryRepository entryRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<TimeSlotResponse> list() {
        return timeSlotRepository.findAllByOrderBySlotOrderAsc().stream()
                .map(TimeSlotResponse::from).toList();
    }

    /**
     * The seven days, creating any missing rows on first read.
     *
     * <p>The days of the week are a fixed fact rather than institutional data, so
     * materialising them is safe. They are created <em>inactive</em>: which days
     * a college actually runs practicals on is a decision for the administrator,
     * not something the system should assume.
     */
    @Transactional
    public List<WorkingDayDto> workingDays() {
        List<WorkingDay> existing = workingDayRepository.findAllByOrderByDayOrderAsc();
        if (existing.size() < DayOfWeek.values().length) {
            for (DayOfWeek day : DayOfWeek.values()) {
                if (workingDayRepository.findByDayOfWeek(day).isPresent()) {
                    continue;
                }
                WorkingDay row = new WorkingDay();
                row.setDayOfWeek(day);
                row.setDayOrder(day.getValue());
                row.setActive(false);
                workingDayRepository.save(row);
            }
            existing = workingDayRepository.findAllByOrderByDayOrderAsc();
        }
        return existing.stream().map(WorkingDayDto::from).toList();
    }

    @Transactional
    public TimeSlotResponse create(TimeSlotRequest request) {
        validate(request);
        if (timeSlotRepository.existsBySlotOrder(request.slotOrder())) {
            throw ApiException.duplicate("Slot order " + request.slotOrder() + " is already used.");
        }
        assertNoOverlap(request, null);

        TimeSlot slot = new TimeSlot();
        apply(slot, request);
        TimeSlot saved = timeSlotRepository.save(slot);
        auditService.record("CREATE", "TimeSlot", saved.getId(), null, describe(saved));
        return TimeSlotResponse.from(saved);
    }

    @Transactional
    public TimeSlotResponse update(Long id, TimeSlotRequest request) {
        TimeSlot slot = find(id);
        String before = describe(slot);
        validate(request);

        timeSlotRepository.findBySlotOrder(request.slotOrder())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw ApiException.duplicate("Slot order " + request.slotOrder() + " is already used.");
                });
        assertNoOverlap(request, id);

        apply(slot, request);
        auditService.record("UPDATE", "TimeSlot", id, before, describe(slot));
        return TimeSlotResponse.from(slot);
    }

    @Transactional
    public void delete(Long id) {
        TimeSlot slot = find(id);
        // A slot referenced by any timetable entry cannot be removed.
        boolean inUse = entryRepository.findAll().stream()
                .anyMatch(e -> e.getStartTimeSlot().getId().equals(id) || e.getEndTimeSlot().getId().equals(id));
        if (inUse) {
            throw ApiException.validation(
                    "This time slot is used by an existing timetable. Deactivate it instead of deleting it.");
        }
        auditService.record("DELETE", "TimeSlot", id, describe(slot), null);
        timeSlotRepository.delete(slot);
    }

    @Transactional
    public List<WorkingDayDto> updateWorkingDays(List<WorkingDayDto> days) {
        for (WorkingDayDto dto : days) {
            WorkingDay day = workingDayRepository.findByDayOfWeek(dto.dayOfWeek())
                    .orElseGet(() -> {
                        WorkingDay created = new WorkingDay();
                        created.setDayOfWeek(dto.dayOfWeek());
                        return created;
                    });
            day.setActive(dto.active());
            day.setDayOrder(dto.dayOrder() != null ? dto.dayOrder() : dto.dayOfWeek().getValue());
            workingDayRepository.save(day);
        }
        auditService.record("UPDATE", "WorkingDay", null, null,
                days.stream().filter(WorkingDayDto::active).map(d -> d.dayOfWeek().name()).toList().toString());
        return workingDays();
    }

    private void validate(TimeSlotRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw ApiException.validation("The end time must be after the start time.");
        }
        if (request.slotOrder() < 1) {
            throw ApiException.validation("Slot order must be 1 or greater.");
        }
    }

    /** Two slots on the same day must not cover overlapping minutes. */
    private void assertNoOverlap(TimeSlotRequest request, Long excludeId) {
        timeSlotRepository.findAllByOrderBySlotOrderAsc().stream()
                .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
                .filter(existing -> request.startTime().isBefore(existing.getEndTime())
                        && existing.getStartTime().isBefore(request.endTime()))
                .findFirst()
                .ifPresent(clash -> {
                    throw ApiException.validation("This period overlaps the existing slot '"
                            + clash.getLabel() + "' (" + clash.getStartTime() + " - " + clash.getEndTime() + ").");
                });
    }

    private void apply(TimeSlot slot, TimeSlotRequest request) {
        slot.setLabel(request.label().trim());
        slot.setStartTime(request.startTime());
        slot.setEndTime(request.endTime());
        slot.setSlotOrder(request.slotOrder());
        slot.setSlotType(request.slotType());
        slot.setActive(request.active() == null || request.active());
    }

    private TimeSlot find(Long id) {
        return timeSlotRepository.findById(id).orElseThrow(() -> ApiException.notFound("Time slot", id));
    }

    private static String describe(TimeSlot slot) {
        return slot.getLabel() + " | " + slot.getStartTime() + "-" + slot.getEndTime()
                + " | order " + slot.getSlotOrder() + " | " + slot.getSlotType();
    }

    /** Active working days, ordered - used across the scheduling engine. */
    @Transactional(readOnly = true)
    public List<DayOfWeek> activeDays() {
        return workingDayRepository.findByActiveTrueOrderByDayOrderAsc().stream()
                .map(WorkingDay::getDayOfWeek).toList();
    }
}
