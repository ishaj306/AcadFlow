package edu.batchmaker.controller;

import edu.batchmaker.domain.enums.RecordStatus;
import edu.batchmaker.dto.common.AccountRequest;
import edu.batchmaker.dto.common.AccountResponse;
import edu.batchmaker.dto.common.MessageResponse;
import edu.batchmaker.dto.common.PageResponse;
import edu.batchmaker.dto.student.StudentImportResult;
import edu.batchmaker.dto.student.StudentParseRequest;
import edu.batchmaker.dto.student.StudentParseResult;
import edu.batchmaker.dto.student.StudentRequest;
import edu.batchmaker.dto.student.StudentResponse;
import edu.batchmaker.service.AccountService;
import edu.batchmaker.service.StudentParserService;
import edu.batchmaker.service.StudentService;
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
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;
    private final AccountService accountService;
    private final StudentParserService studentParserService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public PageResponse<StudentResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) String division,
            @RequestParam(required = false) RecordStatus status,
            @PageableDefault(size = 25, sort = "rollNumber", direction = Sort.Direction.ASC) Pageable pageable) {
        return studentService.search(search, departmentId, semester, division, status, pageable);
    }

    @GetMapping("/divisions")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public List<String> divisions(@RequestParam Long departmentId, @RequestParam Integer semester) {
        return studentService.divisions(departmentId, semester);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public StudentResponse get(@PathVariable Long id) {
        return studentService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public StudentResponse create(@Valid @RequestBody StudentRequest request) {
        return studentService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public StudentResponse update(@PathVariable Long id, @Valid @RequestBody StudentRequest request) {
        return studentService.update(id, request);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public MessageResponse deactivate(@PathVariable Long id) {
        studentService.deactivate(id);
        return MessageResponse.ok("Student deactivated.");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public MessageResponse delete(@PathVariable Long id) {
        studentService.delete(id);
        return MessageResponse.ok("Student deleted.");
    }

    /** Gives an existing student a sign-in account. */
    @PostMapping("/{id}/account")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public AccountResponse createAccount(@PathVariable Long id,
                                         @Valid @RequestBody AccountRequest request) {
        return accountService.createForStudent(id, request);
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public StudentImportResult importCsv(@RequestPart("file") MultipartFile file,
                                         @RequestParam(defaultValue = "false") boolean updateExisting) {
        return studentService.importCsv(file, updateExisting);
    }

    /** Preview: parse pasted roster text into students and a suggested batch split. */
    @PostMapping("/parse")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public StudentParseResult parse(@Valid @RequestBody StudentParseRequest request) {
        return studentParserService.parse(request.rawText(), request.batchSize());
    }
}
