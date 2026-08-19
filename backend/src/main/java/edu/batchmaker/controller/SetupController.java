package edu.batchmaker.controller;

import edu.batchmaker.dto.common.SetupStatusResponse;
import edu.batchmaker.service.SetupService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
public class SetupController {

    private final SetupService setupService;

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public SetupStatusResponse status() {
        return setupService.status();
    }
}
