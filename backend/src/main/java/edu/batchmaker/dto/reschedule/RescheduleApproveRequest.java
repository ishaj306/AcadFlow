package edu.batchmaker.dto.reschedule;

import java.time.DayOfWeek;

/**
 * Approval of a proposed reschedule. Supply {@code candidateId} to accept one of
 * the ranked recommendations, or the explicit fields to place the session
 * manually (spec section 23, "Choose Manually").
 */
public record RescheduleApproveRequest(
        Long candidateId,
        DayOfWeek dayOfWeek,
        Long startSlotId,
        Long facultyId,
        Long labId) {

    public boolean isManual() {
        return candidateId == null;
    }
}
