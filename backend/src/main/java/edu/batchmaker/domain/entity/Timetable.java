package edu.batchmaker.domain.entity;

import edu.batchmaker.domain.enums.TimetableStatus;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * A generated schedule version. Generation always produces a DRAFT; only an
 * explicit approval moves it to PUBLISHED (spec section 18).
 */
@Getter
@Setter
@Entity
@Table(name = "timetables")
public class Timetable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_term_id", nullable = false)
    private AcademicTerm academicTerm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TimetableStatus status = TimetableStatus.DRAFT;

    /** Normalised quality score out of 100. */
    private Double score;

    @Column(name = "hard_violations", nullable = false)
    private Integer hardViolations = 0;

    @Column(name = "soft_penalty")
    private Double softPenalty;

    @Column(name = "solver_status", length = 32)
    private String solverStatus;

    @Column(name = "solver_engine", length = 48)
    private String solverEngine;

    @Column(name = "solver_runtime_ms")
    private Long solverRuntimeMs;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_by")
    private User generatedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    /** Version number within a lineage of published schedules; the first is 1. */
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    /** The previously-published timetable this one replaced, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_id")
    private Timetable supersedes;

    @Column(length = 1024)
    private String notes;
}
