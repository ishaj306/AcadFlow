package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.AcademicTerm;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicTermRepository extends JpaRepository<AcademicTerm, Long> {

    Optional<AcademicTerm> findByCurrentTrue();

    List<AcademicTerm> findAllByOrderByAcademicYearDescSemesterDesc();

    Optional<AcademicTerm> findByAcademicYearAndSemester(String academicYear, Integer semester);
}
