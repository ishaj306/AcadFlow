package edu.batchmaker.domain.entity;

import edu.batchmaker.domain.enums.RescheduleReason;
import edu.batchmaker.domain.enums.RescheduleStatus;
import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Audit record for one rescheduling request: what it was, why it moved, who
 * approved it, and where it landed (spec section 24).
 */
@Getter
@Setter
@Entity
@Table(name = "reschedules")
public class Reschedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "timetable_entry_id", nullable = false)
    private TimetableEntry timetableEntry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "timetable_id", nullable = false)
    private Timetable timetable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RescheduleReason reason;

    @Column(name = "reason_detail", length = 512)
    private String reasonDetail;

    @Column(name = "affected_date")
    private LocalDate affectedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "original_day", nullable = false, length = 12)
    private DayOfWeek originalDay;

    @Column(name = "original_start", nullable = false)
    private LocalTime originalStart;

    @Column(name = "original_end", nullable = false)
    private LocalTime originalEnd;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_faculty_id", nullable = false)
    private Faculty originalFaculty;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_lab_id", nullable = false)
    private Laboratory originalLab;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_day", length = 12)
    private DayOfWeek newDay;

    @Column(name = "new_start")
    private LocalTime newStart;

    @Column(name = "new_end")
    private LocalTime newEnd;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_faculty_id")
    private Faculty newFaculty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_lab_id")
    private Laboratory newLab;

    @Column(name = "candidate_score")
    private Double candidateScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RescheduleStatus status = RescheduleStatus.PROPOSED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiated_by")
    private User initiatedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "applied_at")
    private Instant appliedAt;
}
