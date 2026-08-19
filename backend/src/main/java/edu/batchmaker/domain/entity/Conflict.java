package edu.batchmaker.domain.entity;

import edu.batchmaker.domain.enums.ConflictStatus;
import edu.batchmaker.domain.enums.ConflictType;
import edu.batchmaker.domain.enums.Severity;
import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "conflicts")
public class Conflict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timetable_id")
    private Timetable timetable;

    @Enumerated(EnumType.STRING)
    @Column(name = "conflict_type", nullable = false, length = 40)
    private ConflictType conflictType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Severity severity;

    @Column(nullable = false, length = 1024)
    private String description;

    @Column(name = "suggested_resolution", length = 1024)
    private String suggestedResolution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id")
    private Faculty faculty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private StudentBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_id")
    private Laboratory lab;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    /** The offending entry, and its counterpart for double-booking conflicts. */
    @Column(name = "entry_id")
    private Long entryId;

    @Column(name = "other_entry_id")
    private Long otherEntryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", length = 12)
    private DayOfWeek dayOfWeek;

    @Column(name = "conflict_date")
    private LocalDate conflictDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ConflictStatus status = ConflictStatus.OPEN;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
