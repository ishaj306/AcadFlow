package edu.batchmaker.domain.entity;

import edu.batchmaker.domain.enums.RoleName;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, unique = true)
    private RoleName name;

    @Column(length = 255)
    private String description;
}
