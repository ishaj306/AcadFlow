package edu.batchmaker.dto.common;

import edu.batchmaker.domain.enums.RoleName;

public record AccountResponse(Long userId, String username, RoleName role, String fullName) {
}
