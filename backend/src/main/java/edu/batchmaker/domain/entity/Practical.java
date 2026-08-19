package edu.batchmaker.domain.entity;

import edu.batchmaker.domain.enums.RecordStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * One schedulable demand handed to the optimizer: this batch needs this
 * subject's practical N times a week for {@code durationMin} minutes.
 */
@Getter
@Setter
@Entity
@Table(name = "practicals")
public class Practical {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private StudentBatch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_term_id", nullable = false)
    private AcademicTerm academicTerm;

    /** Soft hint only; the optimizer may pick another qualified faculty. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_faculty_id")
    private Faculty preferredFaculty;

    @Column(name = "sessions_per_week", nullable = false)
    private Integer sessionsPerWeek = 1;

    @Column(name = "duration_min", nullable = false)
    private Integer durationMin = 120;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecordStatus status = RecordStatus.ACTIVE;
}
