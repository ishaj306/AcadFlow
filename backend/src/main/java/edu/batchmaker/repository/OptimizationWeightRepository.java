package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.OptimizationWeight;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OptimizationWeightRepository extends JpaRepository<OptimizationWeight, Long> {

    Optional<OptimizationWeight> findByWeightKey(String weightKey);
}
