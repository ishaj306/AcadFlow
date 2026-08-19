package edu.batchmaker.controller;

import edu.batchmaker.dto.common.AcademicTermRequest;
import edu.batchmaker.dto.common.AcademicTermResponse;
import edu.batchmaker.dto.common.DepartmentRequest;
import edu.batchmaker.dto.common.IdNameResponse;
import edu.batchmaker.dto.common.MessageResponse;
import edu.batchmaker.dto.holiday.HolidayRequest;
import edu.batchmaker.dto.holiday.HolidayResponse;
import edu.batchmaker.dto.timeslot.TimeSlotRequest;
import edu.batchmaker.dto.timeslot.TimeSlotResponse;
import edu.batchmaker.dto.timeslot.WorkingDayDto;
import edu.batchmaker.service.MasterDataService;
import edu.batchmaker.service.TimeSlotService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Institution configuration: terms, departments, working hours, holidays. */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigurationController {

    private final MasterDataService masterDataService;
    private final TimeSlotService timeSlotService;

    @GetMapping("/departments")
    public List<IdNameResponse> departments() {
        return masterDataService.departments();
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public IdNameResponse createDepartment(@Valid @RequestBody DepartmentRequest request) {
        return masterDataService.createDepartment(request);
    }

    @PutMapping("/departments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public IdNameResponse updateDepartment(@PathVariable Long id,
                                           @Valid @RequestBody DepartmentRequest request) {
        return masterDataService.updateDepartment(id, request);
    }

    @DeleteMapping("/departments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public MessageResponse deleteDepartment(@PathVariable Long id) {
        masterDataService.deleteDepartment(id);
        return MessageResponse.ok("Department deleted.");
    }

    @GetMapping("/terms")
    public List<AcademicTermResponse> terms() {
        return masterDataService.terms();
    }

    @PostMapping("/terms")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public AcademicTermResponse createTerm(@Valid @RequestBody AcademicTermRequest request) {
        return masterDataService.createTerm(request);
    }

    @GetMapping("/terms/current")
    public AcademicTermResponse currentTerm() {
        return masterDataService.currentTerm();
    }

    @PostMapping("/terms/{id}/current")
    @PreAuthorize("hasRole('ADMIN')")
    public AcademicTermResponse setCurrentTerm(@PathVariable Long id) {
        return masterDataService.setCurrentTerm(id);
    }

    @GetMapping("/time-slots")
    public List<TimeSlotResponse> timeSlots() {
        return timeSlotService.list();
    }

    @PostMapping("/time-slots")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public TimeSlotResponse createTimeSlot(@Valid @RequestBody TimeSlotRequest request) {
        return timeSlotService.create(request);
    }

    @PutMapping("/time-slots/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TimeSlotResponse updateTimeSlot(@PathVariable Long id, @Valid @RequestBody TimeSlotRequest request) {
        return timeSlotService.update(id, request);
    }

    @DeleteMapping("/time-slots/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public MessageResponse deleteTimeSlot(@PathVariable Long id) {
        timeSlotService.delete(id);
        return MessageResponse.ok("Time slot deleted.");
    }

    @GetMapping("/working-days")
    public List<WorkingDayDto> workingDays() {
        return timeSlotService.workingDays();
    }

    @PutMapping("/working-days")
    @PreAuthorize("hasRole('ADMIN')")
    public List<WorkingDayDto> updateWorkingDays(@RequestBody List<WorkingDayDto> days) {
        return timeSlotService.updateWorkingDays(days);
    }

    @GetMapping("/holidays")
    public List<HolidayResponse> holidays() {
        return masterDataService.holidays();
    }

    @PostMapping("/holidays")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public HolidayResponse addHoliday(@Valid @RequestBody HolidayRequest request) {
        return masterDataService.addHoliday(request);
    }

    @DeleteMapping("/holidays/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public MessageResponse deleteHoliday(@PathVariable Long id) {
        masterDataService.deleteHoliday(id);
        return MessageResponse.ok("Holiday removed.");
    }
}
