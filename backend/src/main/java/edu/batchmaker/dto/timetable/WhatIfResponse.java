package edu.batchmaker.dto.timetable;

import java.util.List;

/**
 * Side-by-side feasibility before and after a hypothetical change, plus a
 * plain-language list of what was changed.
 */
public record WhatIfResponse(
        FeasibilityResponse baseline,
        FeasibilityResponse simulated,
        List<String> changes) {
}
