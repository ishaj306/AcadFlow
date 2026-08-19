package edu.batchmaker.dto.subject;

import edu.batchmaker.domain.entity.Subject;
import edu.batchmaker.domain.enums.RecordStatus;
import edu.batchmaker.domain.enums.SubjectType;

public record SubjectResponse(
        Long id,
        String subjectCode,
        String subjectName,
        Long departmentId,
        String departmentCode,
        String departmentName,
        Integer semester,
        SubjectType subjectType,
        Integer practicalDurationMin,
        Integer sessionsPerWeek,
        Integer studentsPerBatch,
        String requiredLabType,
        RecordStatus status,
        int qualifiedFacultyCount) {

    public static SubjectResponse from(Subject subject, int qualifiedFacultyCount) {
        return new SubjectResponse(
                subject.getId(),
                subject.getSubjectCode(),
                subject.getSubjectName(),
                subject.getDepartment().getId(),
                subject.getDepartment().getCode(),
                subject.getDepartment().getName(),
                subject.getSemester(),
                subject.getSubjectType(),
                subject.getPracticalDurationMin(),
                subject.getSessionsPerWeek(),
                subject.getStudentsPerBatch(),
                subject.getRequiredLabType(),
                subject.getStatus(),
                qualifiedFacultyCount);
    }
}
