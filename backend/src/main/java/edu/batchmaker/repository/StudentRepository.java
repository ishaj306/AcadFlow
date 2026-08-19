package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.Student;
import edu.batchmaker.domain.enums.RecordStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByRollNumber(String rollNumber);

    Optional<Student> findByUserId(Long userId);

    boolean existsByRollNumber(String rollNumber);

    boolean existsByEmail(String email);

    boolean existsByRollNumberAndIdNot(String rollNumber, Long id);

    boolean existsByEmailAndIdNot(String email, Long id);

    long countByStatus(RecordStatus status);

    /**
     * The eligible population for batch generation: everyone active in a given
     * division, ordered by roll number so batches come out in register order.
     */
    List<Student> findByDepartmentIdAndSemesterAndDivisionAndStatusOrderByRollNumberAsc(
            Long departmentId, Integer semester, String division, RecordStatus status);

    @Query("""
            select distinct s.division from Student s
            where s.department.id = :departmentId and s.semester = :semester
              and s.status = edu.batchmaker.domain.enums.RecordStatus.ACTIVE
            order by s.division
            """)
    List<String> findDivisions(@Param("departmentId") Long departmentId, @Param("semester") Integer semester);

    @Query("""
            select s from Student s
            where (:search is null or lower(s.name) like lower(concat('%', :search, '%'))
                                   or lower(s.rollNumber) like lower(concat('%', :search, '%'))
                                   or lower(s.email) like lower(concat('%', :search, '%')))
              and (:departmentId is null or s.department.id = :departmentId)
              and (:semester is null or s.semester = :semester)
              and (:division is null or s.division = :division)
              and (:status is null or s.status = :status)
            """)
    Page<Student> search(@Param("search") String search,
                         @Param("departmentId") Long departmentId,
                         @Param("semester") Integer semester,
                         @Param("division") String division,
                         @Param("status") RecordStatus status,
                         Pageable pageable);
}
