package edu.batchmaker.controller;

import edu.batchmaker.dto.batch.*;
import edu.batchmaker.dto.common.MessageResponse;
import edu.batchmaker.service.BatchGenerationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/batches")
@RequiredArgsConstructor
public class BatchController {

    private final BatchGenerationService batchService;

    @GetMapping
    public List<BatchResponse> list(@RequestParam(required = false) Long subjectId,
                                    @RequestParam(required = false) String division) {
        return batchService.list(subjectId, division);
    }

    @GetMapping("/{id}")
    public BatchResponse get(@PathVariable Long id) {
        return batchService.get(id);
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public BatchGenerationResult generate(@Valid @RequestBody BatchGenerationRequest request) {
        return batchService.generate(request);
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public List<BatchResponse> adjust(@Valid @RequestBody BatchAdjustmentRequest request) {
        return batchService.adjust(request);
    }

    @DeleteMapping("/subject/{subjectId}")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public MessageResponse deleteForSubject(@PathVariable Long subjectId) {
        batchService.deleteBatchesForSubject(subjectId);
        return MessageResponse.ok("Batches removed for the subject.");
    }
}
