package edu.batchmaker.service;

import edu.batchmaker.domain.entity.*;
import edu.batchmaker.dto.commitment.FixedCommitmentRequest;
import edu.batchmaker.dto.commitment.FixedCommitmentResponse;
import edu.batchmaker.exception.ApiException;
import edu.batchmaker.repository.*;
import java.time.DayOfWeek;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages fixed weekly commitments (fixed lectures, meetings, reserved labs).
 * These are entered by an administrator and consumed by the scheduler, which
 * treats them as immovable blocks and folds their hours into faculty workload.
 */
@Service
@RequiredArgsConstructor
public class FixedCommitmentService {

    private final FixedCommitmentRepository repository;
    private final FacultyRepository facultyRepository;
    private final LaboratoryRepository labRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final DepartmentRepository departmentRepository;
    private final AcademicTermRepository termRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<FixedCommitmentResponse> list() {
        return repository.findAllDetailed().stream().map(FixedCommitmentResponse::from).toList();
    }

    @Transactional
    public FixedCommitmentResponse create(FixedCommitmentRequest request) {
        if (request.facultyId() == null && request.labId() == null) {
            throw ApiException.validation(
                    "A fixed commitment must name a faculty member, a laboratory, or both.");
        }

        DayOfWeek day;
        try {
            day = DayOfWeek.valueOf(request.dayOfWeek().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.validation("Unknown day: " + request.dayOfWeek());
        }

        TimeSlot startSlot = timeSlotRepository.findById(request.startSlotId())
                .orElseThrow(() -> ApiException.notFound("Time slot", request.startSlotId()));
        TimeSlot endSlot = timeSlotRepository.findById(request.endSlotId())
                .orElseThrow(() -> ApiException.notFound("Time slot", request.endSlotId()));
        if (startSlot.getSlotOrder() > endSlot.getSlotOrder()) {
            throw ApiException.validation("The start period must not come after the end period.");
        }

        FixedCommitment commitment = new FixedCommitment();
        commitment.setTitle(request.title().trim());
        commitment.setCommitmentType(request.commitmentType() == null || request.commitmentType().isBlank()
                ? "LECTURE" : request.commitmentType().trim().toUpperCase());
        commitment.setDayOfWeek(day);
        commitment.setStartTimeSlot(startSlot);
        commitment.setEndTimeSlot(endSlot);
        commitment.setNote(request.note());

        if (request.facultyId() != null) {
            commitment.setFaculty(facultyRepository.findById(request.facultyId())
                    .orElseThrow(() -> ApiException.notFound("Faculty", request.facultyId())));
        }
        if (request.labId() != null) {
            commitment.setLab(labRepository.findById(request.labId())
                    .orElseThrow(() -> ApiException.notFound("Laboratory", request.labId())));
        }
        if (request.departmentId() != null) {
            commitment.setDepartment(departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> ApiException.notFound("Department", request.departmentId())));
        }
        if (request.academicTermId() != null) {
            commitment.setAcademicTerm(termRepository.findById(request.academicTermId())
                    .orElseThrow(() -> ApiException.notFound("Academic term", request.academicTermId())));
        }

        FixedCommitment saved = repository.save(commitment);
        auditService.record("CREATE", "FixedCommitment", saved.getId(), null,
                saved.getTitle() + " " + day + " " + startSlot.getStartTime() + "-" + endSlot.getEndTime());
        return FixedCommitmentResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        FixedCommitment commitment = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Fixed commitment", id));
        repository.delete(commitment);
        auditService.record("DELETE", "FixedCommitment", id, commitment.getTitle(), null);
    }
}
