package edu.batchmaker.dto.timetable;

import java.util.List;

/**
 * A hypothetical change to test against the current data: labs taken out of
 * service, faculty away, and/or a rise in enrolment. All fields are optional;
 * an empty scenario simply reproduces the baseline.
 */
public record WhatIfScenario(
        List<Long> closedLabIds,
        List<Long> absentFacultyIds,
        Integer additionalStudentPercent) {
}
