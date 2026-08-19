package edu.batchmaker.dto.subject;

import edu.batchmaker.domain.enums.RecordStatus;
import edu.batchmaker.domain.enums.SubjectType;
import jakarta.validation.constraints.*;

public record SubjectRequest(
        @NotBlank(message = "Subject code is required")
        @Size(max = 32)
        String subjectCode,

        @NotBlank(message = "Subject name is required")
        @Size(max = 160)
        String subjectName,

        @NotNull(message = "Department is required")
        Long departmentId,

        @NotNull(message = "Semester is required")
        @Min(value = 1, message = "Semester must be between 1 and 12")
        @Max(value = 12, message = "Semester must be between 1 and 12")
        Integer semester,

        @NotNull(message = "Subject type is required")
        SubjectType subjectType,

        @NotNull(message = "Practical duration is required")
        @Min(value = 15, message = "Practical duration must be at least 15 minutes")
        @Max(value = 600, message = "Practical duration must be 600 minutes or less")
        Integer practicalDurationMin,

        @NotNull(message = "Sessions per week is required")
        @Min(value = 1, message = "Sessions per week must be greater than 0")
        @Max(value = 10, message = "Sessions per week must be 10 or less")
        Integer sessionsPerWeek,

        @NotNull(message = "Batch capacity is required")
        @Min(value = 1, message = "Batch capacity must be greater than 0")
        @Max(value = 500, message = "Batch capacity must be 500 or less")
        Integer studentsPerBatch,

        @NotBlank(message = "Required lab type is required")
        @Size(max = 48)
        String requiredLabType,

        RecordStatus status) {
}
