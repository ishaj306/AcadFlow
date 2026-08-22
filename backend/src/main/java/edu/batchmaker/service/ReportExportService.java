package edu.batchmaker.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import edu.batchmaker.dto.timetable.TimetableDetailResponse;
import edu.batchmaker.dto.timetable.TimetableEntryResponse;
import edu.batchmaker.dto.workload.FacultyWorkloadResponse;
import edu.batchmaker.dto.workload.WorkloadSummaryResponse;
import edu.batchmaker.exception.ApiException;
import edu.batchmaker.exception.ErrorCode;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-side generation of the timetable and workload reports as real
 * {@code .xlsx} and PDF files, built from the live database rather than any
 * client-side snapshot. The browser CSV export and print-to-PDF remain; these
 * add proper spreadsheet and document downloads (spec sections 49–50).
 */
@Service
@RequiredArgsConstructor
public class ReportExportService {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final TimetableService timetableService;
    private final WorkloadService workloadService;

    /** A rendered report: a title line plus a single table. */
    private record Report(String title, String subtitle, List<String> headers, List<List<String>> rows) {
    }

    // ------------------------------------------------------------- public API

    @Transactional(readOnly = true)
    public byte[] timetable(ExportFormat format) {
        return render(buildTimetable(timetableService.current()), format);
    }

    /** Export any specific timetable by id - including an unpublished draft. */
    @Transactional(readOnly = true)
    public byte[] timetable(Long timetableId, ExportFormat format) {
        return render(buildTimetable(timetableService.detail(timetableId)), format);
    }

    @Transactional(readOnly = true)
    public byte[] workload(ExportFormat format) {
        return render(buildWorkload(), format);
    }

    public enum ExportFormat { XLSX, PDF }

    // ------------------------------------------------------------- report data

    private Report buildTimetable(TimetableDetailResponse detail) {
        List<List<String>> rows = new ArrayList<>();
        for (TimetableEntryResponse e : detail.entries()) {
            rows.add(List.of(
                    titleCase(e.dayOfWeek().name()),
                    time(e.startTime()),
                    time(e.endTime()),
                    n(e.subjectCode()),
                    n(e.subjectName()),
                    n(e.batchName()),
                    n(e.division()),
                    String.valueOf(e.studentCount()),
                    n(e.facultyName()),
                    n(e.labName()),
                    n(e.labLocation())));
        }
        String subtitle = detail.timetable().name() + " · " + detail.timetable().academicTermLabel()
                + " · " + titleCase(detail.timetable().status().name());
        return new Report("Practical Timetable", subtitle,
                List.of("Day", "Start", "End", "Code", "Subject", "Batch", "Division",
                        "Students", "Faculty", "Laboratory", "Location"),
                rows);
    }

    private Report buildWorkload() {
        WorkloadSummaryResponse summary = workloadService.summary(null, null);
        List<List<String>> rows = new ArrayList<>();
        for (FacultyWorkloadResponse f : summary.faculty()) {
            rows.add(List.of(
                    n(f.employeeCode()),
                    n(f.facultyName()),
                    n(f.designation()),
                    n(f.departmentCode()),
                    trim1(f.assignedHours()),
                    trim1(f.fixedLoadHours()),
                    trim1(f.totalLoadHours()),
                    String.valueOf(f.maxWeeklyHours()),
                    trim1(f.utilizationPercent()),
                    String.valueOf(f.practicalCount()),
                    n(f.status())));
        }
        String subtitle = "Average utilisation " + trim1(summary.averageUtilizationPercent())
                + "% · " + summary.balanceVerdict();
        return new Report("Faculty Workload", subtitle,
                List.of("Code", "Faculty", "Designation", "Department", "Practical h", "Fixed h",
                        "Total h", "Maximum h", "Utilisation %", "Practicals", "Status"),
                rows);
    }

    // ------------------------------------------------------------- rendering

    private byte[] render(Report report, ExportFormat format) {
        return format == ExportFormat.PDF ? toPdf(report) : toXlsx(report);
    }

    private byte[] toXlsx(Report report) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(report.title());

            org.apache.poi.ss.usermodel.Font bold = workbook.createFont();
            bold.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(bold);

            int r = 0;
            Row titleRow = sheet.createRow(r++);
            titleRow.createCell(0).setCellValue(report.title() + " — " + report.subtitle());

            Row header = sheet.createRow(r++);
            for (int c = 0; c < report.headers().size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(report.headers().get(c));
                cell.setCellStyle(headerStyle);
            }

            for (List<String> dataRow : report.rows()) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < dataRow.size(); c++) {
                    row.createCell(c).setCellValue(dataRow.get(c));
                }
            }
            for (int c = 0; c < report.headers().size(); c++) {
                sheet.autoSizeColumn(c);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.EXPORT_FAILED, "Could not build the spreadsheet: " + ex.getMessage());
        }
    }

    private byte[] toPdf(Report report) {
        Document document = new Document(PageSize.A4.rotate(), 24, 24, 28, 28);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
            Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8);

            document.add(new Paragraph(report.title(), titleFont));
            Paragraph subtitle = new Paragraph(report.subtitle(), subFont);
            subtitle.setSpacingAfter(10);
            document.add(subtitle);

            PdfPTable table = new PdfPTable(report.headers().size());
            table.setWidthPercentage(100);
            Color headerBg = new Color(30, 41, 59);
            for (String head : report.headers()) {
                PdfPCell cell = new PdfPCell(new Phrase(head, headFont));
                cell.setBackgroundColor(headerBg);
                cell.setPadding(4);
                table.addCell(cell);
            }
            boolean stripe = false;
            for (List<String> dataRow : report.rows()) {
                Color bg = stripe ? new Color(241, 245, 249) : Color.WHITE;
                stripe = !stripe;
                for (String value : dataRow) {
                    PdfPCell cell = new PdfPCell(new Phrase(value, cellFont));
                    cell.setBackgroundColor(bg);
                    cell.setPadding(3);
                    table.addCell(cell);
                }
            }
            document.add(table);

            if (report.rows().isEmpty()) {
                document.add(new Paragraph("No data available.", cellFont));
            }
            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            if (document.isOpen()) {
                document.close();
            }
            throw new ApiException(ErrorCode.EXPORT_FAILED, "Could not build the PDF: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------- helpers

    private static String time(LocalTime t) {
        return t == null ? "" : HHMM.format(t);
    }

    private static String titleCase(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.charAt(0) + value.substring(1).toLowerCase();
    }

    private static String trim1(double value) {
        return String.valueOf(Math.round(value * 10.0) / 10.0);
    }

    private static String n(String value) {
        return value == null ? "" : value;
    }
}
