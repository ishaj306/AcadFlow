package edu.batchmaker.dto.lab;

import edu.batchmaker.domain.entity.Laboratory;
import edu.batchmaker.domain.enums.LabStatus;

public record LabResponse(
        Long id,
        String labCode,
        String labName,
        Long departmentId,
        String departmentCode,
        String departmentName,
        Integer capacity,
        String labType,
        String location,
        LabStatus status) {

    public static LabResponse from(Laboratory lab) {
        return new LabResponse(
                lab.getId(),
                lab.getLabCode(),
                lab.getLabName(),
                lab.getDepartment().getId(),
                lab.getDepartment().getCode(),
                lab.getDepartment().getName(),
                lab.getCapacity(),
                lab.getLabType(),
                lab.getLocation(),
                lab.getStatus());
    }
}
