package edu.batchmaker.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Tunable coefficient in the objective function (spec section 16). Stored as
 * data so an administrator can retune the optimizer without a redeploy.
 */
@Getter
@Setter
@Entity
@Table(name = "optimization_weights")
public class OptimizationWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "weight_key", nullable = false, length = 64, unique = true)
    private String weightKey;

    @Column(name = "weight_value", nullable = false)
    private Double weightValue;

    @Column(length = 255)
    private String description;
}
