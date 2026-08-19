package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.Laboratory;
import edu.batchmaker.domain.enums.LabStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LaboratoryRepository extends JpaRepository<Laboratory, Long> {

    Optional<Laboratory> findByLabCode(String labCode);

    boolean existsByLabCode(String labCode);

    boolean existsByLabCodeAndIdNot(String labCode, Long id);

    long countByStatus(LabStatus status);

    List<Laboratory> findByStatusOrderByLabNameAsc(LabStatus status);

    List<Laboratory> findByLabTypeAndStatus(String labType, LabStatus status);

    List<Laboratory> findAllByOrderByLabNameAsc();
}
