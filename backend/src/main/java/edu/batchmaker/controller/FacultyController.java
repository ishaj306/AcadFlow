package edu.batchmaker.controller;

import edu.batchmaker.domain.enums.LeaveStatus;
import edu.batchmaker.domain.enums.RecordStatus;
import edu.batchmaker.domain.enums.RoleName;
import edu.batchmaker.dto.common.AccountRequest;
import edu.batchmaker.dto.common.AccountResponse;
import edu.batchmaker.dto.common.ImportResult;
import edu.batchmaker.dto.common.MessageResponse;
import edu.batchmaker.dto.common.PageResponse;
import edu.batchmaker.dto.faculty.*;
import edu.batchmaker.service.AccountService;
import edu.batchmaker.service.FacultyService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/faculty")
@RequiredArgsConstructor
public class FacultyController {

    private final FacultyService facultyService;
    private final AccountService accountService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public PageResponse<FacultyResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) RecordStatus status,
            @PageableDefault(size = 25, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return facultyService.search(search, departmentId, status, pageable);
    }

    /** Unpaged active list for dropdowns; visible to every signed-in role. */
    @GetMapping("/active")
    public List<FacultyResponse> active() {
        return facultyService.listActive();
    }

    @GetMapping("/{id}")
    public FacultyResponse get(@PathVariable Long id) {
        return facultyService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public FacultyResponse create(@Valid @RequestBody FacultyRequest request) {
        return facultyService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public FacultyResponse update(@PathVariable Long id, @Valid @RequestBody FacultyRequest request) {
        return facultyService.update(id, request);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public MessageResponse deactivate(@PathVariable Long id) {
        facultyService.deactivate(id);
        return MessageResponse.ok("Faculty member deactivated.");
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public ImportResult importCsv(@RequestPart("file") MultipartFile file,
                                  @RequestParam(defaultValue = "false") boolean updateExisting) {
        return facultyService.importCsv(file, updateExisting);
    }

    /** Gives an existing faculty member a sign-in account. */
    @PostMapping("/{id}/account")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public AccountResponse createAccount(@PathVariable Long id,
                                         @Valid @RequestBody AccountRequest request,
                                         @RequestParam(defaultValue = "FACULTY") RoleName role) {
        return accountService.createForFaculty(id, request, role);
    }

    @GetMapping("/{id}/availability")
    public List<AvailabilityEntryDto> availability(@PathVariable Long id) {
        return facultyService.availability(id);
    }

    @PutMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('ADMIN','HOD','FACULTY')")
    public List<AvailabilityEntryDto> replaceAvailability(@PathVariable Long id,
                                                          @Valid @RequestBody List<AvailabilityEntryDto> entries) {
        return facultyService.replaceAvailability(id, entries);
    }

    @GetMapping("/{id}/leaves")
    public List<LeaveResponse> leaves(@PathVariable Long id) {
        return facultyService.leaves(id);
    }

    @GetMapping("/leaves")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public List<LeaveResponse> allLeaves(@RequestParam(required = false) LeaveStatus status) {
        return facultyService.allLeaves(status);
    }

    @PostMapping("/leaves")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','HOD','FACULTY')")
    public LeaveResponse submitLeave(@Valid @RequestBody LeaveRequest request) {
        return facultyService.submitLeave(request);
    }

    @PostMapping("/leaves/{leaveId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public LeaveResponse approveLeave(@PathVariable Long leaveId) {
        return facultyService.decideLeave(leaveId, true);
    }

    @PostMapping("/leaves/{leaveId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public LeaveResponse rejectLeave(@PathVariable Long leaveId) {
        return facultyService.decideLeave(leaveId, false);
    }
}
