package edu.batchmaker.controller;

import edu.batchmaker.dto.workload.FacultyWorkloadResponse;
import edu.batchmaker.dto.workload.WorkloadSummaryResponse;
import edu.batchmaker.service.WorkloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workload")
@RequiredArgsConstructor
public class WorkloadController {

    private final WorkloadService workloadService;

    @GetMapping
    public WorkloadSummaryResponse summary(@RequestParam(required = false) Long timetableId,
                                           @RequestParam(required = false) Long departmentId) {
        return workloadService.summary(timetableId, departmentId);
    }

    @GetMapping("/faculty/{facultyId}")
    public FacultyWorkloadResponse forFaculty(@PathVariable Long facultyId) {
        return workloadService.forFaculty(facultyId);
    }
}
