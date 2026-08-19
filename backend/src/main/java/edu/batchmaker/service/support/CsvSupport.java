package edu.batchmaker.service.support;

import edu.batchmaker.exception.ApiException;
import edu.batchmaker.exception.ErrorCode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

/**
 * Shared spreadsheet reader for the master-data importers (students, faculty,
 * subjects, laboratories). Handles the mechanics — decoding, an RFC-4180 field
 * splitter for CSV, cell extraction for {@code .xlsx}, header validation and
 * typed cell access — so each importer only expresses its own per-row business
 * rules.
 *
 * <p>The upload format is chosen from the file name: {@code .xlsx} is read with
 * Apache POI, anything else as UTF-8 CSV. Either way the caller sees the same
 * {@link Sheet} of header index plus data rows.
 *
 * <p>Header names are normalised to {@code lower_snake_case}, so "Employee Code"
 * and "employee_code" address the same column. A UTF-8 BOM on the first cell is
 * stripped (Excel writes one when saving CSV).
 */
public final class CsvSupport {

    private CsvSupport() {
    }

    /** A non-blank data line together with its 1-based line number in the file. */
    public record Row(int number, String[] cells) {
    }

    /** Parsed header index plus every non-blank data row. */
    public record Sheet(Map<String, Integer> columns, List<Row> rows) {
    }

    /**
     * Reads the whole file, validating that every required header is present.
     * The header line is line 1; data rows carry their real line number so an
     * error message points at the right line even when blanks are skipped.
     * A {@code .xlsx} upload is read as a spreadsheet, anything else as CSV.
     */
    public static Sheet read(MultipartFile file, List<String> requiredHeaders) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.IMPORT_FAILED, "No file was uploaded.");
        }
        String name = file.getOriginalFilename();
        boolean excel = name != null && name.toLowerCase().endsWith(".xlsx");
        return excel ? readXlsx(file, requiredHeaders) : readCsv(file, requiredHeaders);
    }

    private static Sheet readCsv(MultipartFile file, List<String> requiredHeaders) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new ApiException(ErrorCode.IMPORT_FAILED, "The uploaded file is empty.");
            }
            Map<String, Integer> columns = parseHeader(splitCsv(headerLine), requiredHeaders);

            List<Row> rows = new ArrayList<>();
            String line;
            int number = 1;
            while ((line = reader.readLine()) != null) {
                number++;
                if (line.isBlank()) {
                    continue;
                }
                rows.add(new Row(number, splitCsv(line)));
            }
            return new Sheet(columns, rows);
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.IMPORT_FAILED, "The file could not be read: " + ex.getMessage());
        }
    }

    private static Sheet readXlsx(MultipartFile file, List<String> requiredHeaders) {
        DataFormatter formatter = new DataFormatter();
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getNumberOfSheets() == 0
                    ? null : workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new ApiException(ErrorCode.IMPORT_FAILED, "The uploaded spreadsheet is empty.");
            }
            org.apache.poi.ss.usermodel.Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            Map<String, Integer> columns = parseHeader(cellsOf(headerRow, formatter), requiredHeaders);

            List<Row> rows = new ArrayList<>();
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String[] cells = cellsOf(row, formatter);
                boolean allBlank = true;
                for (String cell : cells) {
                    if (cell != null && !cell.isBlank()) {
                        allBlank = false;
                        break;
                    }
                }
                if (allBlank) {
                    continue;
                }
                rows.add(new Row(r + 1, cells));
            }
            return new Sheet(columns, rows);
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new ApiException(ErrorCode.IMPORT_FAILED,
                    "The spreadsheet could not be read: " + ex.getMessage());
        }
    }

    private static String[] cellsOf(org.apache.poi.ss.usermodel.Row row, DataFormatter formatter) {
        int last = row.getLastCellNum();
        if (last < 0) {
            return new String[0];
        }
        String[] out = new String[last];
        for (int c = 0; c < last; c++) {
            Cell cell = row.getCell(c);
            out[c] = cell == null ? "" : formatter.formatCellValue(cell).trim();
        }
        return out;
    }

    private static Map<String, Integer> parseHeader(String[] headers, List<String> requiredHeaders) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            columns.put(normalise(headers[i]), i);
        }
        List<String> missing = requiredHeaders.stream().filter(h -> !columns.containsKey(h)).toList();
        if (!missing.isEmpty()) {
            throw new ApiException(ErrorCode.IMPORT_FAILED,
                    "The CSV is missing required column(s): " + String.join(", ", missing)
                            + ". Expected header: " + String.join(",", requiredHeaders));
        }
        return columns;
    }

    private static String normalise(String header) {
        return header.replace("﻿", "").trim().toLowerCase().replace(' ', '_');
    }

    /** Minimal RFC-4180 splitter: handles quoted fields and escaped quotes. */
    public static String[] splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        out.add(current.toString());
        // A BOM only ever attaches to the very first cell of the first line.
        if (!out.isEmpty()) {
            out.set(0, out.get(0).replace("﻿", ""));
        }
        return out.toArray(String[]::new);
    }

    /** Trimmed cell value, or {@code null} if the column is absent or empty. */
    public static String value(String[] cells, Map<String, Integer> columns, String key) {
        Integer index = columns.get(key);
        if (index == null || index >= cells.length) {
            return null;
        }
        String raw = cells[index].trim();
        return raw.isEmpty() ? null : raw;
    }

    /** Cell value that must be present; throws a validation error otherwise. */
    public static String require(String[] cells, Map<String, Integer> columns, String key) {
        String raw = value(cells, columns, key);
        if (raw == null) {
            throw ApiException.validation("Column '" + key + "' is empty.");
        }
        return raw;
    }

    public static int parseInt(String raw, String field) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw ApiException.validation("Column '" + field + "' must be a whole number, got '" + raw + "'.");
        }
    }
}
