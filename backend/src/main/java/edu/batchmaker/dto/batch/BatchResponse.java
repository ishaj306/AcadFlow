package edu.batchmaker.dto.batch;

import edu.batchmaker.domain.entity.StudentBatch;
import edu.batchmaker.domain.enums.RecordStatus;
import java.util.List;

public record BatchResponse(
        Long id,
        String batchCode,
        String batchName,
        Long subjectId,
        String subjectCode,
        String subjectName,
        Long departmentId,
        String departmentCode,
        String division,
        Integer semester,
        Integer capacity,
        Integer studentCount,
        String requiredLabType,
        RecordStatus status,
        List<BatchMember> students) {

    public record BatchMember(Long studentId, String rollNumber, String name) {
    }

    public static BatchResponse from(StudentBatch batch, List<BatchMember> members) {
        return new BatchResponse(
                batch.getId(),
                batch.getBatchCode(),
                batch.getBatchName(),
                batch.getSubject().getId(),
                batch.getSubject().getSubjectCode(),
                batch.getSubject().getSubjectName(),
                batch.getDepartment().getId(),
                batch.getDepartment().getCode(),
                batch.getDivision(),
                batch.getSemester(),
                batch.getCapacity(),
                batch.getStudentCount(),
                batch.getRequiredLabType(),
                batch.getStatus(),
                members);
    }
}
