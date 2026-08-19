package edu.batchmaker.domain.enums;

public enum SubjectType {
    PRACTICAL,
    THEORY,
    BOTH;

    public boolean hasPractical() {
        return this == PRACTICAL || this == BOTH;
    }
}
