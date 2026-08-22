package edu.batchmaker.service;

import edu.batchmaker.domain.entity.Faculty;
import edu.batchmaker.domain.entity.Laboratory;
import edu.batchmaker.domain.entity.Timetable;
import edu.batchmaker.domain.entity.TimetableEntry;
import edu.batchmaker.domain.enums.RecordStatus;
import edu.batchmaker.dto.assistant.AssistantAnswer;
import edu.batchmaker.dto.timetable.TimetableEntryResponse;
import edu.batchmaker.repository.FacultyRepository;
import edu.batchmaker.repository.LaboratoryRepository;
import edu.batchmaker.repository.StudentRepository;
import edu.batchmaker.repository.TimetableEntryRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A lightweight timetable assistant. It is deliberately not a large language
 * model: it matches a few intents (a day, a division, a faculty member, a lab,
 * or a count) by keyword and answers strictly from the <em>published</em>
 * timetable, so every reply is grounded in real, current data.
 */
@Service
@RequiredArgsConstructor
public class AssistantService {

    private static final List<String> SUGGESTIONS = List.of(
            "What's on today?",
            "Show Division A's schedule",
            "When does Dr. Sharma teach?",
            "How many practicals this week?");

    private final TimetableService timetableService;
    private final TimetableEntryRepository entryRepository;
    private final FacultyRepository facultyRepository;
    private final LaboratoryRepository labRepository;
    private final StudentRepository studentRepository;

    @Transactional(readOnly = true)
    public AssistantAnswer answer(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT).trim();

        Timetable published = timetableService.currentPublished().orElse(null);
        if (published == null) {
            return new AssistantAnswer(
                    "No timetable is published yet, so I can't answer questions about the schedule. "
                            + "Generate and approve a timetable first.",
                    List.of(), SUGGESTIONS);
        }

        List<TimetableEntry> entries = entryRepository.findDetailedByTimetableId(published.getId());

        // ---- count questions --------------------------------------------------
        if (q.contains("how many") || q.contains("count") || q.contains("number of")) {
            return counts(q, entries);
        }

        // ---- a specific day (or today / tomorrow) -----------------------------
        DayOfWeek day = detectDay(q);
        if (day != null) {
            List<TimetableEntry> onDay = filterAndSort(entries, e -> e.getDayOfWeek() == day);
            String when = q.contains("today") ? "today (" + titleCase(day) + ")"
                    : q.contains("tomorrow") ? "tomorrow (" + titleCase(day) + ")"
                    : titleCase(day);
            // Narrow further if the question also names a division or faculty.
            List<TimetableEntry> refined = narrow(q, onDay);
            return list(refined.size() + " practical session(s) scheduled for " + when + ".", refined);
        }

        // ---- a faculty member -------------------------------------------------
        Faculty faculty = detectFaculty(q);
        if (faculty != null) {
            List<TimetableEntry> theirs = filterAndSort(entries,
                    e -> e.getFaculty().getId().equals(faculty.getId()));
            return list(faculty.getName() + " has " + theirs.size() + " session(s) this week.", theirs);
        }

        // ---- a laboratory -----------------------------------------------------
        Laboratory lab = detectLab(q);
        if (lab != null) {
            List<TimetableEntry> inLab = filterAndSort(entries,
                    e -> e.getLab().getId().equals(lab.getId()));
            return list(lab.getLabName() + " is used for " + inLab.size() + " session(s) this week.", inLab);
        }

        // ---- a division -------------------------------------------------------
        String division = detectDivision(q, entries);
        if (division != null) {
            List<TimetableEntry> div = filterAndSort(entries,
                    e -> division.equalsIgnoreCase(e.getBatch().getDivision()));
            return list("Division " + division + " has " + div.size() + " session(s) this week.", div);
        }

        // ---- fallback ---------------------------------------------------------
        return new AssistantAnswer(
                "I can answer questions about the published timetable — try asking about a day, "
                        + "a division, a faculty member, a lab, or a count. For example: \"what's on Monday?\"",
                List.of(), SUGGESTIONS);
    }

    private AssistantAnswer counts(String q, List<TimetableEntry> entries) {
        if (q.contains("student")) {
            long n = studentRepository.count();
            return new AssistantAnswer("There are " + n + " students on record.", List.of(), SUGGESTIONS);
        }
        if (q.contains("faculty") || q.contains("teacher")) {
            long n = facultyRepository.countByStatus(RecordStatus.ACTIVE);
            return new AssistantAnswer("There are " + n + " active faculty members.", List.of(), SUGGESTIONS);
        }
        if (q.contains("lab")) {
            long n = labRepository.count();
            return new AssistantAnswer("There are " + n + " laboratories.", List.of(), SUGGESTIONS);
        }
        return new AssistantAnswer(
                "There are " + entries.size() + " practical sessions in the published timetable this week.",
                List.of(), SUGGESTIONS);
    }

    /** Restricts a day's list further if the question also names a division or faculty. */
    private List<TimetableEntry> narrow(String q, List<TimetableEntry> onDay) {
        Faculty faculty = detectFaculty(q);
        if (faculty != null) {
            return filterAndSort(onDay, e -> e.getFaculty().getId().equals(faculty.getId()));
        }
        String division = detectDivision(q, onDay);
        if (division != null) {
            return filterAndSort(onDay, e -> division.equalsIgnoreCase(e.getBatch().getDivision()));
        }
        return onDay;
    }

    private DayOfWeek detectDay(String q) {
        if (q.contains("today")) {
            return LocalDate.now().getDayOfWeek();
        }
        if (q.contains("tomorrow")) {
            return LocalDate.now().plusDays(1).getDayOfWeek();
        }
        for (DayOfWeek d : DayOfWeek.values()) {
            if (q.contains(d.name().toLowerCase(Locale.ROOT))) {
                return d;
            }
        }
        return null;
    }

    private Faculty detectFaculty(String q) {
        return facultyRepository.findByStatusOrderByNameAsc(RecordStatus.ACTIVE).stream()
                .filter(f -> nameMatches(q, f.getName()))
                .findFirst()
                .orElse(null);
    }

    /** True when the question mentions a distinctive word (3+ letters) of the name.
     *
     * <p>Matches on whole words, not substrings, so "rao" no longer matches
     * "narrow" and "lab" no longer matches "available". */
    private boolean nameMatches(String q, String name) {
        for (String word : name.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
            if (word.length() >= 3
                    && java.util.regex.Pattern.compile("\\b" + word + "\\b").matcher(q).find()) {
                return true;
            }
        }
        return false;
    }

    private Laboratory detectLab(String q) {
        return labRepository.findAll().stream()
                .filter(l -> l.getLabName() != null && nameMatches(q, l.getLabName()))
                .findFirst()
                .orElse(null);
    }

    private String detectDivision(String q, List<TimetableEntry> entries) {
        return entries.stream()
                .map(e -> e.getBatch().getDivision())
                .filter(d -> d != null && !d.isBlank())
                .distinct()
                .filter(d -> q.contains("division " + d.toLowerCase(Locale.ROOT))
                        || q.contains("div " + d.toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElse(null);
    }

    private List<TimetableEntry> filterAndSort(List<TimetableEntry> entries,
                                               java.util.function.Predicate<TimetableEntry> predicate) {
        return entries.stream()
                .filter(predicate)
                .sorted(Comparator.comparing(TimetableEntry::getDayOfWeek)
                        .thenComparing(TimetableEntry::getStartTime))
                .toList();
    }

    private AssistantAnswer list(String answer, List<TimetableEntry> entries) {
        return new AssistantAnswer(
                answer,
                entries.stream().map(TimetableEntryResponse::from).toList(),
                SUGGESTIONS);
    }

    private String titleCase(DayOfWeek day) {
        String name = day.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }
}
