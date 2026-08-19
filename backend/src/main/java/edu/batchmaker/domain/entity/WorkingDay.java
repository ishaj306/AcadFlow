package edu.batchmaker.domain.entity;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "working_days")
public class WorkingDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 12, unique = true)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "day_order", nullable = false)
    private Integer dayOrder;
}
