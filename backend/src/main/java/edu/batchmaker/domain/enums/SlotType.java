package edu.batchmaker.domain.enums;

public enum SlotType {
    TEACHING,
    BREAK,
    LUNCH,
    RESTRICTED,
    SPECIAL;

    /** Practicals may only occupy slots that are actually teachable (H7). */
    public boolean isTeachable() {
        return this == TEACHING || this == SPECIAL;
    }
}
