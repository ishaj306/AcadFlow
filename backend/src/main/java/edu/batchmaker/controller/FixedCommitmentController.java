package edu.batchmaker.controller;

import edu.batchmaker.dto.commitment.FixedCommitmentRequest;
import edu.batchmaker.dto.commitment.FixedCommitmentResponse;
import edu.batchmaker.dto.common.MessageResponse;
import edu.batchmaker.service.FixedCommitmentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Fixed weekly commitments: lectures, meetings and reserved laboratory slots. */
@RestController
@RequestMapping("/api/fixed-commitments")
@RequiredArgsConstructor
public class FixedCommitmentController {

    private final FixedCommitmentService service;

    @GetMapping
    public List<FixedCommitmentResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public FixedCommitmentResponse create(@Valid @RequestBody FixedCommitmentRequest request) {
        return service.create(request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public MessageResponse delete(@PathVariable Long id) {
        service.delete(id);
        return MessageResponse.ok("Fixed commitment removed.");
    }
}
