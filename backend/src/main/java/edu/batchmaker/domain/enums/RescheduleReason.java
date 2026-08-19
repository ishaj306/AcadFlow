package edu.batchmaker.domain.enums;

public enum RescheduleReason {
    FACULTY_LEAVE("Faculty leave"),
    FACULTY_UNAVAILABLE("Faculty unavailability"),
    LAB_MAINTENANCE("Laboratory maintenance"),
    HOLIDAY("Declared holiday"),
    EMERGENCY_CANCELLATION("Emergency cancellation"),
    ADMINISTRATIVE("Administrative change"),
    CONFLICT_RESOLUTION("Conflict resolution");

    private final String label;

    RescheduleReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
