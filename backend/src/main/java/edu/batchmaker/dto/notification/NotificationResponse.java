package edu.batchmaker.dto.notification;

import edu.batchmaker.domain.entity.Notification;
import edu.batchmaker.domain.enums.NotificationCategory;
import edu.batchmaker.domain.enums.NotificationSeverity;
import java.time.Instant;

public record NotificationResponse(
        Long id,
        String title,
        String body,
        NotificationCategory category,
        NotificationSeverity severity,
        String relatedEntityType,
        Long relatedEntityId,
        boolean read,
        Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getBody(),
                notification.getCategory(),
                notification.getSeverity(),
                notification.getRelatedEntityType(),
                notification.getRelatedEntityId(),
                notification.getReadAt() != null,
                notification.getCreatedAt());
    }
}
