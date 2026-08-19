package edu.batchmaker.domain.entity;

import edu.batchmaker.domain.enums.LabStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "laboratories")
public class Laboratory extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lab_code", nullable = false, length = 32, unique = true)
    private String labCode;

    @Column(name = "lab_name", nullable = false, length = 128)
    private String labName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "lab_type", nullable = false, length = 48)
    private String labType;

    @Column(length = 128)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LabStatus status = LabStatus.ACTIVE;
}
