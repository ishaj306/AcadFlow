package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.TimeSlot;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    List<TimeSlot> findAllByOrderBySlotOrderAsc();

    List<TimeSlot> findByActiveTrueOrderBySlotOrderAsc();

    Optional<TimeSlot> findBySlotOrder(Integer slotOrder);

    boolean existsBySlotOrder(Integer slotOrder);

    /** Slots a practical may actually occupy (active + teachable). */
    @Query("""
            select ts from TimeSlot ts
            where ts.active = true
              and ts.slotType in (edu.batchmaker.domain.enums.SlotType.TEACHING,
                                  edu.batchmaker.domain.enums.SlotType.SPECIAL)
            order by ts.slotOrder
            """)
    List<TimeSlot> findSchedulableSlots();
}
