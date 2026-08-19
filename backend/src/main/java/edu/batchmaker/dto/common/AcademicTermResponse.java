package edu.batchmaker.dto.common;

import edu.batchmaker.domain.entity.AcademicTerm;
import java.time.LocalDate;

public record AcademicTermResponse(
        Long id,
        String academicYear,
        Integer semester,
        LocalDate startDate,
        LocalDate endDate,
        boolean current,
        String label) {

    public static AcademicTermResponse from(AcademicTerm term) {
        return new AcademicTermResponse(
                term.getId(),
                term.getAcademicYear(),
                term.getSemester(),
                term.getStartDate(),
                term.getEndDate(),
                term.isCurrent(),
                term.getLabel());
    }
}
