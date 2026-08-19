package edu.batchmaker.domain.entity;

import edu.batchmaker.domain.enums.RecordStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * A practical batch: a slice of one division that attends a given subject's
 * practical together. Batch membership lives in {@link BatchStudent} because a
 * student belongs to one batch per subject, not one batch overall.
 */
@Getter
@Setter
@Entity
@Table(name = "student_batches")
public class StudentBatch extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_code", nullable = false, length = 48, unique = true)
    private String batchCode;

    /** Display name such as "Batch A". */
    @Column(name = "batch_name", nullable = false, length = 64)
    private String batchName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_term_id", nullable = false)
    private AcademicTerm academicTerm;

    @Column(nullable = false, length = 8)
    private String division;

    @Column(nullable = false)
    private Integer semester;

    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "student_count", nullable = false)
    private Integer studentCount = 0;

    @Column(name = "required_lab_type", nullable = false, length = 48)
    private String requiredLabType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecordStatus status = RecordStatus.ACTIVE;
}
