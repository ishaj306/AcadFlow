package edu.batchmaker.controller;

import edu.batchmaker.dto.common.ImportResult;
import edu.batchmaker.dto.common.MessageResponse;
import edu.batchmaker.dto.subject.SubjectRequest;
import edu.batchmaker.dto.subject.SubjectResponse;
import edu.batchmaker.service.SubjectService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;

    @GetMapping
    public List<SubjectResponse> list(@RequestParam(required = false) Long departmentId,
                                      @RequestParam(required = false) Integer semester,
                                      @RequestParam(defaultValue = "false") boolean practicalOnly) {
        return subjectService.list(departmentId, semester, practicalOnly);
    }

    @GetMapping("/lab-types")
    public List<String> labTypes() {
        return subjectService.labTypes();
    }

    @GetMapping("/{id}")
    public SubjectResponse get(@PathVariable Long id) {
        return subjectService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public SubjectResponse create(@Valid @RequestBody SubjectRequest request) {
        return subjectService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public SubjectResponse update(@PathVariable Long id, @Valid @RequestBody SubjectRequest request) {
        return subjectService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public MessageResponse delete(@PathVariable Long id) {
        subjectService.delete(id);
        return MessageResponse.ok("Subject deleted.");
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN','HOD')")
    public ImportResult importCsv(@RequestPart("file") MultipartFile file,
                                  @RequestParam(defaultValue = "false") boolean updateExisting) {
        return subjectService.importCsv(file, updateExisting);
    }
}
