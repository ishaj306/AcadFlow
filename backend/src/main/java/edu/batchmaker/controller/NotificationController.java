package edu.batchmaker.controller;

import edu.batchmaker.dto.common.MessageResponse;
import edu.batchmaker.dto.notification.NotificationResponse;
import edu.batchmaker.service.NotificationService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> list(@PageableDefault(size = 50) Pageable pageable) {
        return notificationService.mine(pageable);
    }

    @GetMapping("/recent")
    public List<NotificationResponse> recent() {
        return notificationService.mine();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notificationService.unreadCount());
    }

    @PostMapping("/read-all")
    public MessageResponse markAllRead() {
        notificationService.markAllRead();
        return MessageResponse.ok("All notifications marked as read.");
    }

    @PostMapping("/{id}/read")
    public MessageResponse markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return MessageResponse.ok("Notification marked as read.");
    }
}
