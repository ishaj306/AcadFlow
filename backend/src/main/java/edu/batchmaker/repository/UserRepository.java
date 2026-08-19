package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.User;
import edu.batchmaker.domain.enums.RoleName;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u from User u join fetch u.role where u.username = :username")
    Optional<User> findByUsernameWithRole(String username);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<User> findByRoleName(RoleName roleName);
}
