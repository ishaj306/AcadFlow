package edu.batchmaker.service;

import edu.batchmaker.domain.entity.Notification;
import edu.batchmaker.domain.entity.StudentBatch;
import edu.batchmaker.domain.entity.TimetableEntry;
import edu.batchmaker.domain.entity.User;
import edu.batchmaker.domain.enums.NotificationCategory;
import edu.batchmaker.domain.enums.NotificationSeverity;
import edu.batchmaker.domain.enums.RoleName;
import edu.batchmaker.dto.notification.NotificationResponse;
import edu.batchmaker.repository.*;
import edu.batchmaker.security.CurrentUser;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** In-app notifications for schedule changes (spec section 28). */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final BatchStudentRepository batchStudentRepository;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public List<NotificationResponse> mine() {
        Long userId = currentUser.requirePrincipal().getId();
        return notificationRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> mine(Pageable pageable) {
        Long userId = currentUser.requirePrincipal().getId();
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationResponse::from).getContent();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notificationRepository.countByUserIdAndReadAtIsNull(currentUser.requirePrincipal().getId());
    }

    @Transactional
    public void markAllRead() {
        notificationRepository.markAllRead(currentUser.requirePrincipal().getId(), java.time.Instant.now());
    }

    @Transactional
    public void markRead(Long id) {
        notificationRepository.findById(id).ifPresent(n -> {
            if (n.getUser().getId().equals(currentUser.requirePrincipal().getId()) && n.getReadAt() == null) {
                n.setReadAt(java.time.Instant.now());
            }
        });
    }

    /** Notifies coordinators and admins that a timetable went live. */
    @Transactional
    public void notifyTimetablePublished(String timetableName, Long timetableId, int entryCount) {
        Set<User> recipients = new LinkedHashSet<>();
        recipients.addAll(userRepository.findByRoleName(RoleName.ADMIN));
        recipients.addAll(userRepository.findByRoleName(RoleName.HOD));

        String body = "The practical timetable \"" + timetableName + "\" has been approved and published with "
                + entryCount + " scheduled sessions.";
        recipients.forEach(user -> save(user, "Timetable published", body,
                NotificationCategory.TIMETABLE_PUBLISHED, NotificationSeverity.SUCCESS,
                "Timetable", timetableId));
    }

    /**
     * Notifies everyone affected by a moved practical: the faculty member(s)
     * involved, every student in the batch, and the coordinators.
     */
    @Transactional
    public void notifyScheduleChange(TimetableEntry entry,
                                     DayOfWeek oldDay, LocalTime oldStart,
                                     String oldFacultyName, String oldLabName,
                                     String reason) {
        String title = "Practical schedule updated";
        String body = entry.getSubject().getSubjectName() + " (" + entry.getBatch().getBatchName() + ")"
                + " moved from " + pretty(oldDay) + " " + oldStart
                + " to " + pretty(entry.getDayOfWeek()) + " " + entry.getStartTime()
                + ". Laboratory: " + entry.getLab().getLabName()
                + ". Faculty: " + entry.getFaculty().getName()
                + (oldFacultyName != null && !oldFacultyName.equals(entry.getFaculty().getName())
                        ? " (previously " + oldFacultyName + ")" : "")
                + (oldLabName != null && !oldLabName.equals(entry.getLab().getLabName())
                        ? ". Previously in " + oldLabName : "")
                + ". Reason: " + reason + ".";

        Set<User> recipients = new LinkedHashSet<>();

        if (entry.getFaculty().getUser() != null) {
            recipients.add(entry.getFaculty().getUser());
        }
        recipients.addAll(studentUsersOf(entry.getBatch()));
        recipients.addAll(userRepository.findByRoleName(RoleName.HOD));
        recipients.addAll(userRepository.findByRoleName(RoleName.ADMIN));

        recipients.forEach(user -> save(user, title, body,
                NotificationCategory.SCHEDULE_CHANGE, NotificationSeverity.WARNING,
                "TimetableEntry", entry.getId()));

        log.info("Schedule-change notification delivered to {} user(s) for entry {}",
                recipients.size(), entry.getId());
    }

    private List<User> studentUsersOf(StudentBatch batch) {
        List<User> users = new ArrayList<>();
        batchStudentRepository.findByBatchIdWithStudent(batch.getId()).forEach(membership -> {
            var student = studentRepository.findById(membership.getStudent().getId()).orElse(null);
            if (student != null && student.getUser() != null) {
                users.add(student.getUser());
            }
        });
        return users;
    }

    private void save(User user, String title, String body, NotificationCategory category,
                      NotificationSeverity severity, String entityType, Long entityId) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setBody(body.length() > 1024 ? body.substring(0, 1024) : body);
        notification.setCategory(category);
        notification.setSeverity(severity);
        notification.setRelatedEntityType(entityType);
        notification.setRelatedEntityId(entityId);
        notificationRepository.save(notification);
    }

    private static String pretty(DayOfWeek day) {
        String name = day.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }
}
