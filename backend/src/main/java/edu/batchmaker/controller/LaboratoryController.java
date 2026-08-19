package edu.batchmaker.controller;

import edu.batchmaker.domain.enums.LabStatus;
import edu.batchmaker.dto.common.ImportResult;
import edu.batchmaker.dto.faculty.AvailabilityEntryDto;
import edu.batchmaker.dto.lab.LabRequest;
import edu.batchmaker.dto.lab.LabResponse;
import edu.batchmaker.service.LaboratoryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/labs")
@RequiredArgsConstructor
public class LaboratoryController {

    private final LaboratoryService laboratoryService;

    @GetMapping
    public List<LabResponse> list(@RequestParam(required = false) Long departmentId,
                                  @RequestParam(required = false) String labType,
                                  @RequestParam(required = false) LabStatus status) {
        return laboratoryService.list(departmentId, labType, status);
    }

    @GetMapping("/{id}")
    public LabResponse get(@PathVariable Long id) {
        return laboratoryService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public LabResponse create(@Valid @RequestBody LabRequest request) {
        return laboratoryService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public LabResponse update(@PathVariable Long id, @Valid @RequestBody LabRequest request) {
        return laboratoryService.update(id, request);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public LabResponse changeStatus(@PathVariable Long id, @RequestParam LabStatus status) {
        return laboratoryService.changeStatus(id, status);
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public ImportResult importCsv(@RequestPart("file") MultipartFile file,
                                  @RequestParam(defaultValue = "false") boolean updateExisting) {
        return laboratoryService.importCsv(file, updateExisting);
    }

    @GetMapping("/{id}/availability")
    public List<AvailabilityEntryDto> availability(@PathVariable Long id) {
        return laboratoryService.availability(id);
    }

    @PutMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public List<AvailabilityEntryDto> replaceAvailability(@PathVariable Long id,
                                                          @Valid @RequestBody List<AvailabilityEntryDto> entries) {
        return laboratoryService.replaceAvailability(id, entries);
    }
}
