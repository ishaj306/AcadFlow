package edu.batchmaker.dto.faculty;

import edu.batchmaker.domain.entity.Faculty;
import edu.batchmaker.domain.enums.RecordStatus;
import edu.batchmaker.dto.common.IdNameResponse;
import java.util.List;

public record FacultyResponse(
        Long id,
        String employeeCode,
        String name,
        String email,
        Long departmentId,
        String departmentCode,
        String departmentName,
        String designation,
        Integer maxWeeklyHours,
        RecordStatus status,
        List<IdNameResponse> subjects) {

    public static FacultyResponse from(Faculty faculty, List<IdNameResponse> subjects) {
        return new FacultyResponse(
                faculty.getId(),
                faculty.getEmployeeCode(),
                faculty.getName(),
                faculty.getEmail(),
                faculty.getDepartment().getId(),
                faculty.getDepartment().getCode(),
                faculty.getDepartment().getName(),
                faculty.getDesignation(),
                faculty.getMaxWeeklyHours(),
                faculty.getStatus(),
                subjects);
    }
}
