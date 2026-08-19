package edu.batchmaker.domain.enums;

/**
 * A single table models both hard availability and soft preference:
 * UNAVAILABLE blocks assignment (H5/H6), PREFERRED earns a soft-constraint
 * bonus (S1), AVAILABLE is neutral.
 */
public enum AvailabilityType {
    AVAILABLE,
    UNAVAILABLE,
    PREFERRED;

    public boolean blocksAssignment() {
        return this == UNAVAILABLE;
    }
}
