package edu.batchmaker.domain.entity;

import edu.batchmaker.domain.enums.SlotType;
import jakarta.persistence.*;
import java.time.Duration;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

/**
 * One configurable period of the college day. Working hours are data, never
 * hard-coded (spec section 11).
 */
@Getter
@Setter
@Entity
@Table(name = "time_slots")
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String label;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** Chronological position; also used to test slot adjacency. */
    @Column(name = "slot_order", nullable = false, unique = true)
    private Integer slotOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "slot_type", nullable = false, length = 16)
    private SlotType slotType = SlotType.TEACHING;

    @Column(nullable = false)
    private boolean active = true;

    public int getDurationMinutes() {
        return (int) Duration.between(startTime, endTime).toMinutes();
    }

    public boolean isSchedulable() {
        return active && slotType.isTeachable();
    }
}
