package edu.batchmaker.dto.student;

import edu.batchmaker.domain.entity.Student;
import edu.batchmaker.domain.enums.RecordStatus;

public record StudentResponse(
        Long id,
        String rollNumber,
        String name,
        String email,
        Long departmentId,
        String departmentCode,
        String departmentName,
        Integer semester,
        Integer studyYear,
        String division,
        RecordStatus status,
        boolean hasLogin) {

    public static StudentResponse from(Student student) {
        return new StudentResponse(
                student.getId(),
                student.getRollNumber(),
                student.getName(),
                student.getEmail(),
                student.getDepartment().getId(),
                student.getDepartment().getCode(),
                student.getDepartment().getName(),
                student.getSemester(),
                student.getStudyYear(),
                student.getDivision(),
                student.getStatus(),
                student.getUser() != null);
    }
}
