package edu.batchmaker.domain.entity;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import lombok.Getter;
import lombok.Setter;

/**
 * A recurring weekly obligation the practical optimiser must work around but
 * cannot move: a fixed lecture, a departmental meeting, or an externally
 * reserved laboratory slot (spec sections 8 and 11).
 *
 * <p>Two effects:
 * <ul>
 *   <li>the covered (day, period) cells are blocked for the named faculty member
 *       and/or laboratory, exactly like an unavailability;</li>
 *   <li>the duration counts towards the faculty member's total weekly load, so
 *       "fixed lecture + practical" is the figure the workload objective and the
 *       reports use - not practical hours alone.</li>
 * </ul>
 *
 * <p>At least one of {@code faculty} or {@code lab} must be set, otherwise the
 * commitment would block nothing.
 */
@Getter
@Setter
@Entity
@Table(name = "fixed_commitments")
public class FixedCommitment extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String title;

    /** LECTURE, MEETING, RESERVED, ... - descriptive only. */
    @Column(name = "commitment_type", nullable = false, length = 24)
    private String commitmentType = "LECTURE";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id")
    private Faculty faculty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_id")
    private Laboratory lab;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    /** Null means the commitment applies in every term. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_term_id")
    private AcademicTerm academicTerm;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 12)
    private DayOfWeek dayOfWeek;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "start_time_slot_id", nullable = false)
    private TimeSlot startTimeSlot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "end_time_slot_id", nullable = false)
    private TimeSlot endTimeSlot;

    @Column(length = 255)
    private String note;
}
