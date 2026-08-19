package edu.batchmaker.repository;

import edu.batchmaker.domain.entity.Subject;
import edu.batchmaker.domain.enums.RecordStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    Optional<Subject> findBySubjectCode(String subjectCode);

    boolean existsBySubjectCode(String subjectCode);

    boolean existsBySubjectCodeAndIdNot(String subjectCode, Long id);

    List<Subject> findByStatusOrderBySubjectNameAsc(RecordStatus status);

    List<Subject> findByDepartmentIdAndSemesterAndStatus(Long departmentId, Integer semester, RecordStatus status);

    /** Subjects that actually need practical sessions scheduled. */
    @Query("""
            select s from Subject s
            where s.status = edu.batchmaker.domain.enums.RecordStatus.ACTIVE
              and s.subjectType in (edu.batchmaker.domain.enums.SubjectType.PRACTICAL,
                                    edu.batchmaker.domain.enums.SubjectType.BOTH)
            order by s.subjectName
            """)
    List<Subject> findSchedulablePracticalSubjects();

    @Query("select distinct s.requiredLabType from Subject s order by s.requiredLabType")
    List<String> findDistinctLabTypes();
}
