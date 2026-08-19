package edu.batchmaker.service;

import edu.batchmaker.domain.entity.AuditLog;
import edu.batchmaker.repository.AuditLogRepository;
import edu.batchmaker.repository.UserRepository;
import edu.batchmaker.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Records administrative actions for the audit trail (spec section 40). */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public void record(String action, String entityType, Long entityId) {
        record(action, entityType, entityId, null, null);
    }

    /**
     * Writes in its own transaction so a failure to audit can never roll back
     * the business operation that succeeded.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, Long entityId, String oldValue, String newValue) {
        try {
            AuditLog entry = new AuditLog();
            currentUser.principal().ifPresent(principal -> {
                entry.setUsername(principal.getUsername());
                userRepository.findById(principal.getId()).ifPresent(entry::setUser);
            });
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setOldValue(truncate(oldValue));
            entry.setNewValue(truncate(newValue));

            HttpServletRequest request = currentRequest();
            if (request != null) {
                entry.setIpAddress(clientIp(request));
                entry.setUserAgent(truncate(request.getHeader("User-Agent"), 255));
            }
            auditLogRepository.save(entry);
        } catch (Exception ex) {
            log.warn("Failed to write audit entry for {} {}", action, entityType, ex);
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> list(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    private static String truncate(String value) {
        return truncate(value, 2048);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
