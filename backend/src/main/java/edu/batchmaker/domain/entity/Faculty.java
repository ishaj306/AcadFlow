package edu.batchmaker.domain.entity;

import edu.batchmaker.domain.enums.RecordStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "faculty")
public class Faculty extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "employee_code", nullable = false, length = 32, unique = true)
    private String employeeCode;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 160, unique = true)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(nullable = false, length = 64)
    private String designation;

    /** Upper bound used by the workload balancing objective (S2). */
    @Column(name = "max_weekly_hours", nullable = false)
    private Integer maxWeeklyHours = 18;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecordStatus status = RecordStatus.ACTIVE;
}
