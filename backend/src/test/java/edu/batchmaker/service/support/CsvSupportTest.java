package edu.batchmaker.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.batchmaker.exception.ApiException;
import edu.batchmaker.exception.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** Unit tests for the shared spreadsheet importer used by every master-data upload. */
class CsvSupportTest {

    private static final List<String> REQUIRED = List.of("roll_number", "name", "division");

    private static MockMultipartFile csv(String body) {
        return new MockMultipartFile("file", "data.csv", "text/csv",
                body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void readsRowsAndTracksRealLineNumbers() {
        CsvSupport.Sheet sheet = CsvSupport.read(
                csv("roll_number,name,division\nR1,Asha,A\n\nR2,Ben,B\n"), REQUIRED);

        assertThat(sheet.rows()).hasSize(2);
        // The blank line 3 is skipped but must not shift the reported line number.
        assertThat(sheet.rows().get(0).number()).isEqualTo(2);
        assertThat(sheet.rows().get(1).number()).isEqualTo(4);
        assertThat(CsvSupport.require(sheet.rows().get(1).cells(), sheet.columns(), "name"))
                .isEqualTo("Ben");
    }

    @Test
    void headerNamesAreNormalisedAndBomStripped() {
        CsvSupport.Sheet sheet = CsvSupport.read(
                csv("﻿Roll Number,Name,Division\nR1,Asha,A\n"), REQUIRED);

        assertThat(sheet.columns()).containsKeys("roll_number", "name", "division");
    }

    @Test
    void quotedFieldMayContainCommasAndEscapedQuotes() {
        CsvSupport.Sheet sheet = CsvSupport.read(
                csv("roll_number,name,division\nR1,\"Doe, \"\"Jr\"\" John\",A\n"), REQUIRED);

        assertThat(CsvSupport.require(sheet.rows().get(0).cells(), sheet.columns(), "name"))
                .isEqualTo("Doe, \"Jr\" John");
    }

    @Test
    void missingRequiredHeaderIsRejected() {
        assertThatThrownBy(() -> CsvSupport.read(csv("roll_number,name\nR1,Asha\n"), REQUIRED))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.IMPORT_FAILED))
                .hasMessageContaining("division");
    }

    @Test
    void requireRejectsAnEmptyCellButValueReturnsNull() {
        CsvSupport.Sheet sheet = CsvSupport.read(
                csv("roll_number,name,division\nR1,,A\n"), REQUIRED);
        String[] cells = sheet.rows().get(0).cells();

        assertThat(CsvSupport.value(cells, sheet.columns(), "name")).isNull();
        assertThatThrownBy(() -> CsvSupport.require(cells, sheet.columns(), "name"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void parseIntRejectsNonNumericValues() {
        assertThatThrownBy(() -> CsvSupport.parseInt("twelve", "semester"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("semester");
        assertThat(CsvSupport.parseInt(" 12 ", "semester")).isEqualTo(12);
    }

    @Test
    void readsXlsxWithNumericCellsAsPlainText() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("roll_number");
            header.createCell(1).setCellValue("name");
            header.createCell(2).setCellValue("division");
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("R1");
            data.createCell(1).setCellValue("Asha");
            data.createCell(2).setCellValue(5); // numeric — must come back as "5", not "5.0"
            workbook.write(out);
            bytes = out.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "data.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
        CsvSupport.Sheet sheet = CsvSupport.read(file, REQUIRED);

        assertThat(sheet.rows()).hasSize(1);
        assertThat(CsvSupport.require(sheet.rows().get(0).cells(), sheet.columns(), "division"))
                .isEqualTo("5");
    }
}
