package edu.batchmaker.domain.entity;

import edu.batchmaker.domain.enums.EntryStatus;
import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

/** A single scheduled practical session. */
@Getter
@Setter
@Entity
@Table(name = "timetable_entries")
public class TimetableEntry extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "timetable_id", nullable = false)
    private Timetable timetable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practical_id")
    private Practical practical;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private StudentBatch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lab_id", nullable = false)
    private Laboratory lab;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 12)
    private DayOfWeek dayOfWeek;

    /** A practical may span several consecutive slots; both ends are recorded. */
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

    @Column(name = "session_index", nullable = false)
    private Integer sessionIndex = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EntryStatus status = EntryStatus.SCHEDULED;

    /** True when this entry's time range overlaps the given range on the same day. */
    public boolean overlaps(DayOfWeek day, LocalTime otherStart, LocalTime otherEnd) {
        return dayOfWeek == day && startTime.isBefore(otherEnd) && otherStart.isBefore(endTime);
    }
}
