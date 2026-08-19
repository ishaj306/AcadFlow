package edu.batchmaker.controller;

import edu.batchmaker.dto.common.MessageResponse;
import edu.batchmaker.dto.timetable.*;
import edu.batchmaker.service.TimetableService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/timetable")
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableService timetableService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public List<TimetableSummaryResponse> list() {
        return timetableService.list();
    }

    /** The published schedule everyone works from. */
    @GetMapping("/current")
    public TimetableDetailResponse current() {
        return timetableService.current();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public TimetableDetailResponse get(@PathVariable Long id) {
        return timetableService.detail(id);
    }

    /** Resource pre-check: can the current data be scheduled, and if not, what to change. */
    @PostMapping("/feasibility")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public FeasibilityResponse feasibility() {
        return timetableService.feasibilityAudit();
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public GenerationResultResponse generate(@Valid @RequestBody TimetableGenerateRequest request) {
        return timetableService.generate(request);
    }

    /** Generate several draft options at once, each tuned to a different priority. */
    @PostMapping("/generate-options")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public List<GenerationResultResponse> generateOptions(
            @Valid @RequestBody TimetableGenerateRequest request) {
        return timetableService.generateOptions(request);
    }

    /** Compare current feasibility against a hypothetical scenario. */
    @PostMapping("/what-if")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public WhatIfResponse whatIf(@RequestBody WhatIfScenario scenario) {
        return timetableService.whatIf(scenario);
    }

    @GetMapping("/{id}/validate")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public ValidationSummary validate(@PathVariable Long id) {
        return timetableService.validate(id);
    }

    // ------------------------------------------------------- manual grid edits

    @PostMapping("/{id}/entries")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public TimetableDetailResponse addEntry(@PathVariable Long id,
                                            @Valid @RequestBody EntryEditRequest request) {
        return timetableService.addEntry(id, request);
    }

    @PutMapping("/{id}/entries/{entryId}")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public TimetableDetailResponse updateEntry(@PathVariable Long id, @PathVariable Long entryId,
                                               @Valid @RequestBody EntryEditRequest request) {
        return timetableService.updateEntry(id, entryId, request);
    }

    @DeleteMapping("/{id}/entries/{entryId}")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public TimetableDetailResponse deleteEntry(@PathVariable Long id, @PathVariable Long entryId) {
        return timetableService.deleteEntry(id, entryId);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public TimetableDetailResponse approve(@PathVariable Long id) {
        return timetableService.approve(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public TimetableSummaryResponse reject(@PathVariable Long id,
                                           @RequestParam(required = false) String reason) {
        return timetableService.reject(id, reason);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public MessageResponse delete(@PathVariable Long id) {
        timetableService.delete(id);
        return MessageResponse.ok("Draft timetable deleted.");
    }

    // ------------------------------------------------------------ scoped views

    @GetMapping("/faculty/{facultyId}")
    public TimetableDetailResponse forFaculty(@PathVariable Long facultyId) {
        return timetableService.forFaculty(facultyId);
    }

    @GetMapping("/student/{studentId}")
    public TimetableDetailResponse forStudent(@PathVariable Long studentId) {
        return timetableService.forStudent(studentId);
    }

    @GetMapping("/batch/{batchId}")
    public TimetableDetailResponse forBatch(@PathVariable Long batchId) {
        return timetableService.forBatch(batchId);
    }

    @GetMapping("/lab/{labId}")
    public TimetableDetailResponse forLab(@PathVariable Long labId) {
        return timetableService.forLab(labId);
    }

    @GetMapping("/division")
    public TimetableDetailResponse forDivision(@RequestParam(required = false) Long departmentId,
                                               @RequestParam(required = false) Integer semester,
                                               @RequestParam String division) {
        return timetableService.forDivision(departmentId, semester, division);
    }
}
