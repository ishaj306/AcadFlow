package edu.batchmaker.domain.enums;

public enum LabStatus {
    ACTIVE,
    MAINTENANCE,
    INACTIVE;

    /** Only ACTIVE labs may receive timetable assignments (hard constraint H6). */
    public boolean isSchedulable() {
        return this == ACTIVE;
    }
}
