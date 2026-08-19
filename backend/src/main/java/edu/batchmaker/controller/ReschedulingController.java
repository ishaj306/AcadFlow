package edu.batchmaker.controller;

import edu.batchmaker.domain.enums.RescheduleStatus;
import edu.batchmaker.dto.reschedule.*;
import edu.batchmaker.service.ReschedulingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rescheduling")
@RequiredArgsConstructor
public class ReschedulingController {

    private final ReschedulingService reschedulingService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public List<RescheduleResponse> list(@RequestParam(required = false) RescheduleStatus status) {
        return reschedulingService.list(status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public RescheduleResponse get(@PathVariable Long id) {
        return reschedulingService.get(id);
    }

    /** Finds and ranks alternatives without changing the timetable. */
    @PostMapping("/analyze")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public RescheduleResponse analyze(@Valid @RequestBody RescheduleAnalyzeRequest request) {
        return reschedulingService.analyze(request);
    }

    /** Raises proposals for every practical an approved leave disrupts. */
    @PostMapping("/from-leave/{leaveId}")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public List<RescheduleResponse> analyzeForLeave(@PathVariable Long leaveId) {
        return reschedulingService.analyzeForLeave(leaveId);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public RescheduleResponse approve(@PathVariable Long id,
                                      @RequestBody RescheduleApproveRequest request) {
        return reschedulingService.approve(id, request);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public RescheduleResponse reject(@PathVariable Long id,
                                     @RequestParam(required = false) String reason) {
        return reschedulingService.reject(id, reason);
    }
}
