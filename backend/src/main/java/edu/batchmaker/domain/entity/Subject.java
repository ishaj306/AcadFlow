package edu.batchmaker.domain.entity;

import edu.batchmaker.domain.enums.RecordStatus;
import edu.batchmaker.domain.enums.SubjectType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "subjects")
public class Subject extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_code", nullable = false, length = 32, unique = true)
    private String subjectCode;

    @Column(name = "subject_name", nullable = false, length = 160)
    private String subjectName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(nullable = false)
    private Integer semester;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 16)
    private SubjectType subjectType = SubjectType.PRACTICAL;

    @Column(name = "practical_duration_min", nullable = false)
    private Integer practicalDurationMin = 120;

    @Column(name = "sessions_per_week", nullable = false)
    private Integer sessionsPerWeek = 1;

    /** Maximum students in one practical batch for this subject. */
    @Column(name = "students_per_batch", nullable = false)
    private Integer studentsPerBatch = 30;

    @Column(name = "required_lab_type", nullable = false, length = 48)
    private String requiredLabType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecordStatus status = RecordStatus.ACTIVE;
}
