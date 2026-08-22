package edu.batchmaker.controller;

import edu.batchmaker.service.ReportExportService;
import edu.batchmaker.service.ReportExportService.ExportFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Server-generated report downloads. The reports read the live published
 * timetable and workload from the database and stream a real {@code .xlsx} or
 * PDF, complementing the browser-side CSV export and print-to-PDF.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','HOD')")
public class ReportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportExportService exportService;

    @GetMapping("/timetable/export")
    public ResponseEntity<byte[]> timetable(@RequestParam(defaultValue = "xlsx") String format) {
        ExportFormat fmt = parse(format);
        return file(exportService.timetable(fmt), "timetable", fmt);
    }

    /** Export a specific timetable by id - works for an unpublished draft too. */
    @GetMapping("/timetable/{timetableId}/export")
    public ResponseEntity<byte[]> timetableById(@PathVariable Long timetableId,
                                                @RequestParam(defaultValue = "xlsx") String format) {
        ExportFormat fmt = parse(format);
        return file(exportService.timetable(timetableId, fmt), "timetable-" + timetableId, fmt);
    }

    @GetMapping("/workload/export")
    public ResponseEntity<byte[]> workload(@RequestParam(defaultValue = "xlsx") String format) {
        ExportFormat fmt = parse(format);
        return file(exportService.workload(fmt), "faculty-workload", fmt);
    }

    private static ExportFormat parse(String format) {
        return "pdf".equalsIgnoreCase(format) ? ExportFormat.PDF : ExportFormat.XLSX;
    }

    private ResponseEntity<byte[]> file(byte[] body, String base, ExportFormat format) {
        boolean pdf = format == ExportFormat.PDF;
        String filename = base + (pdf ? ".pdf" : ".xlsx");
        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF : XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
