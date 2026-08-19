package edu.batchmaker.dto.timetable;

import java.util.List;

/**
 * Independent re-check of a generated schedule (spec section 19). The backend
 * never trusts the solver's own report; these numbers come from re-examining
 * the persisted entries against the database.
 */
public record ValidationSummary(
        boolean publishable,
        int hardViolations,
        int facultyConflicts,
        int labConflicts,
        int studentConflicts,
        int capacityViolations,
        int availabilityViolations,
        int holidayConflicts,
        int qualificationViolations,
        double labUtilizationPercent,
        String facultyWorkloadBalance,
        int overloadedFaculty,
        List<String> warnings) {
}
