package edu.batchmaker.domain.enums;

/** Conflict categories detected by the conflict engine (spec section 20). */
public enum ConflictType {
    FACULTY_DOUBLE_BOOKING("Faculty double booking"),
    LAB_DOUBLE_BOOKING("Laboratory double booking"),
    STUDENT_BATCH_OVERLAP("Student batch overlap"),
    CAPACITY_VIOLATION("Laboratory capacity exceeded"),
    FACULTY_UNAVAILABLE("Faculty unavailable in assigned slot"),
    LAB_UNAVAILABLE("Laboratory unavailable in assigned slot"),
    HOLIDAY_CONFLICT("Practical scheduled on a holiday"),
    FACULTY_ON_LEAVE("Faculty on approved leave"),
    DUPLICATE_ALLOCATION("Duplicate practical allocation"),
    NON_TEACHING_SLOT("Practical scheduled outside teaching hours"),
    FACULTY_NOT_QUALIFIED("Faculty not qualified for subject"),
    LAB_TYPE_MISMATCH("Laboratory type does not match subject requirement"),
    WORKLOAD_EXCEEDED("Faculty weekly hour limit exceeded");

    private final String label;

    ConflictType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
