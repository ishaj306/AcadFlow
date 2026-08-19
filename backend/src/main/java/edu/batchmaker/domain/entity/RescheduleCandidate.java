package edu.batchmaker.domain.entity;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

/** One ranked alternative slot offered to the coordinator (spec section 23). */
@Getter
@Setter
@Entity
@Table(name = "reschedule_candidates")
public class RescheduleCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reschedule_id", nullable = false)
    private Reschedule reschedule;

    @Column(name = "rank_order", nullable = false)
    private Integer rankOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 12)
    private DayOfWeek dayOfWeek;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "start_time_slot_id", nullable = false)
    private TimeSlot startTimeSlot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "end_time_slot_id", nullable = false)
    private TimeSlot endTimeSlot;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lab_id", nullable = false)
    private Laboratory lab;

    @Column(nullable = false)
    private Double score;

    /** Human-readable breakdown of how the score was reached. */
    @Column(name = "score_breakdown", length = 1024)
    private String scoreBreakdown;

    @Column(nullable = false)
    private boolean selected = false;
}
