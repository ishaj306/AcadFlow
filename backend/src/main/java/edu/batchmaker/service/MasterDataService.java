package edu.batchmaker.service;

import edu.batchmaker.domain.entity.AcademicTerm;
import edu.batchmaker.domain.entity.Department;
import edu.batchmaker.domain.entity.Holiday;
import edu.batchmaker.dto.common.AcademicTermRequest;
import edu.batchmaker.dto.common.AcademicTermResponse;
import edu.batchmaker.dto.common.DepartmentRequest;
import edu.batchmaker.dto.common.IdNameResponse;
import edu.batchmaker.dto.holiday.HolidayRequest;
import edu.batchmaker.dto.holiday.HolidayResponse;
import edu.batchmaker.exception.ApiException;
import edu.batchmaker.exception.ErrorCode;
import edu.batchmaker.repository.*;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Departments, academic terms and the holiday calendar. */
@Service
@RequiredArgsConstructor
public class MasterDataService {

    private final DepartmentRepository departmentRepository;
    private final AcademicTermRepository termRepository;
    private final HolidayRepository holidayRepository;
    private final StudentRepository studentRepository;
    private final FacultyRepository facultyRepository;
    private final SubjectRepository subjectRepository;
    private final LaboratoryRepository labRepository;
    private final AuditService auditService;

    // ------------------------------------------------------------ departments

    @Transactional(readOnly = true)
    public List<IdNameResponse> departments() {
        return departmentRepository.findAll().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(d -> new IdNameResponse(d.getId(), d.getCode(), d.getName()))
                .toList();
    }

    @Transactional
    public IdNameResponse createDepartment(DepartmentRequest request) {
        String code = request.code().trim().toUpperCase();
        if (departmentRepository.existsByCode(code)) {
            throw ApiException.duplicate("A department with code '" + code + "' already exists.");
        }
        Department department = new Department();
        department.setCode(code);
        department.setName(request.name().trim());
        Department saved = departmentRepository.save(department);
        auditService.record("CREATE", "Department", saved.getId(), null, code + " " + saved.getName());
        return new IdNameResponse(saved.getId(), saved.getCode(), saved.getName());
    }

    @Transactional
    public IdNameResponse updateDepartment(Long id, DepartmentRequest request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Department", id));
        String before = department.getCode() + " " + department.getName();
        String code = request.code().trim().toUpperCase();

        departmentRepository.findByCode(code)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw ApiException.duplicate("A department with code '" + code + "' already exists.");
                });

        department.setCode(code);
        department.setName(request.name().trim());
        auditService.record("UPDATE", "Department", id, before, code + " " + department.getName());
        return new IdNameResponse(department.getId(), department.getCode(), department.getName());
    }

    /** Refuses to delete a department that anything still belongs to. */
    @Transactional
    public void deleteDepartment(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Department", id));

        long students = studentRepository.search(null, id, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements();
        long faculty = facultyRepository.search(null, id, null,
                org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements();
        long subjects = subjectRepository.findAll().stream()
                .filter(s -> s.getDepartment().getId().equals(id)).count();
        long labs = labRepository.findAll().stream()
                .filter(l -> l.getDepartment().getId().equals(id)).count();

        if (students + faculty + subjects + labs > 0) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE,
                    "This department still has " + students + " student(s), " + faculty + " faculty, "
                            + subjects + " subject(s) and " + labs + " laboratory(ies). "
                            + "Move or remove them before deleting the department.");
        }

        auditService.record("DELETE", "Department", id, department.getCode(), null);
        departmentRepository.delete(department);
    }

    // ----------------------------------------------------------------- terms

    @Transactional(readOnly = true)
    public List<AcademicTermResponse> terms() {
        return termRepository.findAllByOrderByAcademicYearDescSemesterDesc().stream()
                .map(AcademicTermResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AcademicTermResponse currentTerm() {
        return AcademicTermResponse.from(requireCurrentTerm());
    }

    /** The term every scheduling operation defaults to. */
    @Transactional(readOnly = true)
    public AcademicTerm requireCurrentTerm() {
        return termRepository.findByCurrentTrue()
                .orElseThrow(() -> new ApiException(ErrorCode.ILLEGAL_STATE,
                        "No academic term is marked as current. Add one in Settings before scheduling."));
    }

    @Transactional
    public AcademicTermResponse createTerm(AcademicTermRequest request) {
        String year = request.academicYear().trim();
        if (!request.endDate().isAfter(request.startDate())) {
            throw ApiException.validation("The end date must be after the start date.");
        }
        termRepository.findByAcademicYearAndSemester(year, request.semester()).ifPresent(existing -> {
            throw ApiException.duplicate(
                    "Semester " + request.semester() + " of " + year + " already exists.");
        });

        AcademicTerm term = new AcademicTerm();
        term.setAcademicYear(year);
        term.setSemester(request.semester());
        term.setStartDate(request.startDate());
        term.setEndDate(request.endDate());
        term.setCurrent(false);
        AcademicTerm saved = termRepository.save(term);

        // The first term created is automatically the current one, otherwise the
        // installation would have data but nothing to schedule against.
        boolean firstTerm = termRepository.count() == 1;
        if (request.makeCurrent() || firstTerm) {
            termRepository.findAll().forEach(t -> t.setCurrent(t.getId().equals(saved.getId())));
        }

        auditService.record("CREATE", "AcademicTerm", saved.getId(), null, saved.getLabel());
        return AcademicTermResponse.from(saved);
    }

    @Transactional
    public AcademicTermResponse setCurrentTerm(Long termId) {
        AcademicTerm target = termRepository.findById(termId)
                .orElseThrow(() -> ApiException.notFound("Academic term", termId));
        termRepository.findAll().forEach(term -> term.setCurrent(term.getId().equals(termId)));
        auditService.record("SET_CURRENT_TERM", "AcademicTerm", termId, null, target.getLabel());
        return AcademicTermResponse.from(target);
    }

    // ---------------------------------------------------------------- holidays

    @Transactional(readOnly = true)
    public List<HolidayResponse> holidays() {
        return holidayRepository.findAllByOrderByHolidayDateAsc().stream()
                .map(HolidayResponse::from).toList();
    }

    @Transactional
    public HolidayResponse addHoliday(HolidayRequest request) {
        Holiday holiday = new Holiday();
        holiday.setHolidayDate(request.holidayDate());
        holiday.setName(request.name().trim());
        holiday.setDescription(request.description());
        if (request.departmentId() != null) {
            Department department = departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> ApiException.notFound("Department", request.departmentId()));
            holiday.setDepartment(department);
        }
        Holiday saved = holidayRepository.save(holiday);
        auditService.record("CREATE", "Holiday", saved.getId(), null,
                saved.getHolidayDate() + " " + saved.getName());
        return HolidayResponse.from(saved);
    }

    @Transactional
    public void deleteHoliday(Long id) {
        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Holiday", id));
        auditService.record("DELETE", "Holiday", id, holiday.getHolidayDate() + " " + holiday.getName(), null);
        holidayRepository.delete(holiday);
    }

    /** True when the date is a declared holiday for the given department. */
    @Transactional(readOnly = true)
    public boolean isHoliday(LocalDate date, Long departmentId) {
        return holidayRepository.findByHolidayDate(date).stream()
                .anyMatch(h -> h.getDepartment() == null
                        || (departmentId != null && h.getDepartment().getId().equals(departmentId)));
    }
}
