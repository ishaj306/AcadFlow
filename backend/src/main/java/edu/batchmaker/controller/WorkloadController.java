package edu.batchmaker.controller;

import edu.batchmaker.dto.workload.FacultyWorkloadResponse;
import edu.batchmaker.dto.workload.WorkloadSummaryResponse;
import edu.batchmaker.service.WorkloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workload")
@RequiredArgsConstructor
public class WorkloadController {

    private final WorkloadService workloadService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public WorkloadSummaryResponse summary(@RequestParam(required = false) Long timetableId,
                                           @RequestParam(required = false) Long departmentId) {
        return workloadService.summary(timetableId, departmentId);
    }

    // Per-faculty view is used by each teacher's own dashboard, so FACULTY is
    // allowed here (consistent with the other faculty-scoped reads); the
    // all-faculty summary above stays ADMIN/HOD only.
    @GetMapping("/faculty/{facultyId}")
    @PreAuthorize("hasAnyRole('ADMIN','HOD','FACULTY')")
    public FacultyWorkloadResponse forFaculty(@PathVariable Long facultyId) {
        return workloadService.forFaculty(facultyId);
    }
}
