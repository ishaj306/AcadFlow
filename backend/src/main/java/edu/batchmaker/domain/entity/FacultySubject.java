package edu.batchmaker.domain.entity;

import edu.batchmaker.domain.enums.Proficiency;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Qualification matrix. The optimizer will not assign a subject to a faculty
 * member unless a row exists here (spec section 26).
 */
@Getter
@Setter
@Entity
@Table(name = "faculty_subjects")
public class FacultySubject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Proficiency proficiency = Proficiency.PRIMARY;
}
