package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.Role;
import edu.batchmaker.domain.enums.RoleName;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(RoleName name);
}
