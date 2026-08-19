package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.Faculty;
import edu.batchmaker.domain.enums.RecordStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FacultyRepository extends JpaRepository<Faculty, Long> {

    Optional<Faculty> findByEmployeeCode(String employeeCode);

    Optional<Faculty> findByUserId(Long userId);

    boolean existsByEmployeeCode(String employeeCode);

    boolean existsByEmail(String email);

    boolean existsByEmployeeCodeAndIdNot(String employeeCode, Long id);

    boolean existsByEmailAndIdNot(String email, Long id);

    long countByStatus(RecordStatus status);

    List<Faculty> findByStatusOrderByNameAsc(RecordStatus status);

    List<Faculty> findByDepartmentIdAndStatusOrderByNameAsc(Long departmentId, RecordStatus status);

    @Query("""
            select f from Faculty f
            where (:search is null or lower(f.name) like lower(concat('%', :search, '%'))
                                   or lower(f.employeeCode) like lower(concat('%', :search, '%'))
                                   or lower(f.email) like lower(concat('%', :search, '%')))
              and (:departmentId is null or f.department.id = :departmentId)
              and (:status is null or f.status = :status)
            """)
    Page<Faculty> search(@Param("search") String search,
                         @Param("departmentId") Long departmentId,
                         @Param("status") RecordStatus status,
                         Pageable pageable);
}
