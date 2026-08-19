package edu.batchmaker.dto.auth;

import edu.batchmaker.domain.enums.RoleName;

/**
 * Identity payload the frontend uses for routing and menu visibility.
 * {@code facultyId} / {@code studentId} are populated when the account is
 * linked to a faculty or student record.
 */
public record CurrentUserResponse(
        Long id,
        String username,
        String fullName,
        String email,
        RoleName role,
        Long departmentId,
        String departmentName,
        Long facultyId,
        Long studentId) {
}
