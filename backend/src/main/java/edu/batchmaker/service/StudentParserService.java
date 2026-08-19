package edu.batchmaker.service;

import edu.batchmaker.dto.student.StudentParseResult;
import edu.batchmaker.dto.student.StudentParseResult.ParsedStudent;
import edu.batchmaker.dto.student.StudentParseResult.SuggestedBatch;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Turns free-form pasted roster text into structured student records and a
 * suggested batch split. It tolerates the common shapes people paste — one
 * student per line as "roll name", "name roll", comma- or tab-separated, with an
 * optional trailing division letter — and reports lines it could not read rather
 * than guessing.
 */
@Service
public class StudentParserService {

    private static final int DEFAULT_BATCH_SIZE = 30;
    // A roll number: a token with at least one digit and one letter or 4+ digits.
    private static final Pattern ROLL = Pattern.compile("^(?=.*\\d)[A-Za-z0-9/-]{3,}$");
    // A trailing division marker: a single letter, optionally after "div"/"division".
    private static final Pattern DIVISION = Pattern.compile("^(?:div(?:ision)?)?([A-Za-z])$");

    public StudentParseResult parse(String rawText, Integer batchSize) {
        int size = batchSize == null || batchSize < 1 ? DEFAULT_BATCH_SIZE : batchSize;
        List<ParsedStudent> students = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        String[] lines = rawText == null ? new String[0] : rawText.split("\\r?\\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            // Skip an obvious header row.
            String lower = line.toLowerCase();
            if (lower.startsWith("roll") || lower.startsWith("s.no") || lower.startsWith("sr")
                    || lower.startsWith("name,") || lower.equals("name")) {
                continue;
            }

            String[] tokens = line.split("[,\\t]+|\\s{2,}|\\s+");
            List<String> parts = new ArrayList<>();
            for (String t : tokens) {
                if (!t.isBlank()) {
                    parts.add(t.trim());
                }
            }
            if (parts.isEmpty()) {
                continue;
            }

            // Find the roll-number token.
            int rollIndex = -1;
            for (int i = 0; i < parts.size(); i++) {
                if (ROLL.matcher(parts.get(i)).matches()) {
                    rollIndex = i;
                    break;
                }
            }
            if (rollIndex < 0) {
                warnings.add("Could not find a roll number in: \"" + line + "\"");
                continue;
            }
            String roll = parts.get(rollIndex);

            // A trailing single-letter token is treated as the division.
            String division = null;
            List<String> nameParts = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) {
                if (i == rollIndex) {
                    continue;
                }
                var m = DIVISION.matcher(parts.get(i));
                if (i == parts.size() - 1 && m.matches()) {
                    division = m.group(1).toUpperCase();
                } else {
                    nameParts.add(parts.get(i));
                }
            }

            String name = String.join(" ", nameParts).trim();
            if (name.isEmpty()) {
                warnings.add("Found roll " + roll + " but no name in: \"" + line + "\"");
                continue;
            }
            students.add(new ParsedStudent(roll, name, division));
        }

        return new StudentParseResult(students, splitIntoBatches(students, size), warnings);
    }

    /** Groups students by division (in first-seen order) and splits each into batches. */
    private List<SuggestedBatch> splitIntoBatches(List<ParsedStudent> students, int size) {
        Map<String, Integer> byDivision = new LinkedHashMap<>();
        for (ParsedStudent s : students) {
            String division = s.division() == null ? "" : s.division();
            byDivision.merge(division, 1, Integer::sum);
        }

        List<SuggestedBatch> batches = new ArrayList<>();
        byDivision.forEach((division, count) -> {
            int batchCount = (int) Math.ceil(count / (double) size);
            for (int i = 0; i < batchCount; i++) {
                int inThis = Math.min(size, count - i * size);
                String label = division.isEmpty()
                        ? "Batch " + (i + 1)
                        : "Div " + division + " — Batch " + (i + 1);
                batches.add(new SuggestedBatch(label, division.isEmpty() ? null : division, inThis));
            }
        });
        return batches;
    }
}
