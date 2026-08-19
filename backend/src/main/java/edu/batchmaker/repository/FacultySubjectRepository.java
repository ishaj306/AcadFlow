package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.FacultySubject;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FacultySubjectRepository extends JpaRepository<FacultySubject, Long> {

    List<FacultySubject> findByFacultyId(Long facultyId);

    List<FacultySubject> findBySubjectId(Long subjectId);

    boolean existsByFacultyIdAndSubjectId(Long facultyId, Long subjectId);

    void deleteByFacultyId(Long facultyId);

    /** Qualification pairs as [facultyId, subjectId], for building solver input. */
    @Query("select fs.faculty.id, fs.subject.id from FacultySubject fs")
    List<Object[]> findAllQualificationPairs();

    @Query("""
            select fs.faculty.id from FacultySubject fs
            where fs.subject.id = :subjectId
              and fs.faculty.status = edu.batchmaker.domain.enums.RecordStatus.ACTIVE
            """)
    List<Long> findQualifiedFacultyIds(Long subjectId);
}
