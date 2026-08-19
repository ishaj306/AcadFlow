package edu.batchmaker.controller;

import edu.batchmaker.domain.enums.ConflictStatus;
import edu.batchmaker.dto.conflict.ConflictResponse;
import edu.batchmaker.service.ConflictDetectionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conflicts")
@RequiredArgsConstructor
public class ConflictController {

    private final ConflictDetectionService conflictService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public List<ConflictResponse> list(@RequestParam(required = false) ConflictStatus status) {
        return conflictService.list(status);
    }

    /** Re-runs detection against the current database state. */
    @PostMapping("/detect/{timetableId}")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public List<ConflictResponse> detect(@PathVariable Long timetableId) {
        return conflictService.detectAndStore(timetableId);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public ConflictResponse updateStatus(@PathVariable Long id, @RequestParam ConflictStatus status) {
        return conflictService.updateStatus(id, status);
    }
}
