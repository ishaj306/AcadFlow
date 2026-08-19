package edu.batchmaker.service;

import edu.batchmaker.domain.entity.Department;
import edu.batchmaker.domain.entity.LabAvailability;
import edu.batchmaker.domain.entity.Laboratory;
import edu.batchmaker.domain.entity.TimeSlot;
import edu.batchmaker.domain.enums.AvailabilityType;
import edu.batchmaker.domain.enums.LabStatus;
import edu.batchmaker.dto.common.ImportResult;
import edu.batchmaker.dto.faculty.AvailabilityEntryDto;
import edu.batchmaker.dto.lab.LabRequest;
import edu.batchmaker.dto.lab.LabResponse;
import edu.batchmaker.exception.ApiException;
import edu.batchmaker.repository.DepartmentRepository;
import edu.batchmaker.repository.LabAvailabilityRepository;
import edu.batchmaker.repository.LaboratoryRepository;
import edu.batchmaker.repository.TimeSlotRepository;
import edu.batchmaker.service.support.CsvSupport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class LaboratoryService {

    private final LaboratoryRepository labRepository;
    private final LabAvailabilityRepository availabilityRepository;
    private final DepartmentRepository departmentRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<LabResponse> list(Long departmentId, String labType, LabStatus status) {
        return labRepository.findAllByOrderByLabNameAsc().stream()
                .filter(l -> departmentId == null || l.getDepartment().getId().equals(departmentId))
                .filter(l -> labType == null || l.getLabType().equalsIgnoreCase(labType))
                .filter(l -> status == null || l.getStatus() == status)
                .map(LabResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LabResponse get(Long id) {
        return LabResponse.from(find(id));
    }

    @Transactional
    public LabResponse create(LabRequest request) {
        if (labRepository.existsByLabCode(request.labCode())) {
            throw ApiException.duplicate("Lab code '" + request.labCode() + "' already exists.");
        }
        Laboratory lab = new Laboratory();
        apply(lab, request);
        Laboratory saved = labRepository.save(lab);
        auditService.record("CREATE", "Laboratory", saved.getId(), null, describe(saved));
        return LabResponse.from(saved);
    }

    @Transactional
    public LabResponse update(Long id, LabRequest request) {
        Laboratory lab = find(id);
        String before = describe(lab);
        if (labRepository.existsByLabCodeAndIdNot(request.labCode(), id)) {
            throw ApiException.duplicate("Lab code '" + request.labCode() + "' belongs to another laboratory.");
        }
        apply(lab, request);
        auditService.record("UPDATE", "Laboratory", id, before, describe(lab));
        return LabResponse.from(lab);
    }

    /**
     * Flipping a lab to MAINTENANCE does not silently move practicals; the
     * conflict engine raises them so a coordinator can reschedule explicitly.
     */
    @Transactional
    public LabResponse changeStatus(Long id, LabStatus status) {
        Laboratory lab = find(id);
        LabStatus before = lab.getStatus();
        lab.setStatus(status);
        auditService.record("CHANGE_STATUS", "Laboratory", id, before.name(), status.name());
        return LabResponse.from(lab);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityEntryDto> availability(Long labId) {
        find(labId);
        return availabilityRepository.findByLabId(labId).stream()
                .map(a -> new AvailabilityEntryDto(a.getDayOfWeek(), a.getTimeSlot().getId(),
                        a.getAvailability(), a.getNote()))
                .sorted(Comparator.comparing(AvailabilityEntryDto::dayOfWeek)
                        .thenComparing(AvailabilityEntryDto::timeSlotId))
                .toList();
    }

    @Transactional
    public List<AvailabilityEntryDto> replaceAvailability(Long labId, List<AvailabilityEntryDto> entries) {
        Laboratory lab = find(labId);
        availabilityRepository.deleteByLabId(labId);
        availabilityRepository.flush();

        for (AvailabilityEntryDto entry : entries) {
            if (entry.availability() == AvailabilityType.AVAILABLE) {
                continue;
            }
            TimeSlot slot = timeSlotRepository.findById(entry.timeSlotId())
                    .orElseThrow(() -> ApiException.notFound("Time slot", entry.timeSlotId()));
            LabAvailability record = new LabAvailability();
            record.setLab(lab);
            record.setDayOfWeek(entry.dayOfWeek());
            record.setTimeSlot(slot);
            record.setAvailability(entry.availability());
            record.setNote(entry.note());
            availabilityRepository.save(record);
        }

        auditService.record("UPDATE_AVAILABILITY", "Laboratory", labId, null,
                entries.size() + " cell(s) submitted");
        return availability(labId);
    }

    // ---------------------------------------------------------------- import

    private static final List<String> REQUIRED_CSV_HEADERS = List.of(
            "lab_code", "lab_name", "department_code", "capacity", "lab_type");

    /**
     * Bulk upload of laboratories from CSV or {@code .xlsx}. Rows are validated
     * independently; a bad row is reported and skipped rather than aborting the
     * whole import. An existing lab code is updated only when
     * {@code updateExisting} is set, otherwise it is reported as skipped.
     */
    @Transactional
    public ImportResult importCsv(MultipartFile file, boolean updateExisting) {
        CsvSupport.Sheet sheet = CsvSupport.read(file, REQUIRED_CSV_HEADERS);
        Map<String, Department> departmentCache = new HashMap<>();
        List<ImportResult.RowError> errors = new ArrayList<>();
        int imported = 0;
        int updated = 0;

        for (CsvSupport.Row row : sheet.rows()) {
            String code = CsvSupport.value(row.cells(), sheet.columns(), "lab_code");
            try {
                switch (upsertRow(row.cells(), sheet.columns(), departmentCache, updateExisting)) {
                    case INSERTED -> imported++;
                    case UPDATED -> updated++;
                    case SKIPPED_DUPLICATE -> errors.add(new ImportResult.RowError(row.number(), code,
                            "Lab code already exists. Enable 'update existing' to overwrite."));
                }
            } catch (ApiException ex) {
                errors.add(new ImportResult.RowError(row.number(), code, ex.getMessage()));
            } catch (RuntimeException ex) {
                errors.add(new ImportResult.RowError(row.number(), code,
                        "Row could not be processed: " + ex.getMessage()));
            }
        }

        auditService.record("IMPORT", "Laboratory", null, null,
                "imported=" + imported + " updated=" + updated + " errors=" + errors.size());
        return new ImportResult(sheet.rows().size(), imported, updated, errors.size(), errors);
    }

    private enum RowOutcome { INSERTED, UPDATED, SKIPPED_DUPLICATE }

    private RowOutcome upsertRow(String[] cells, Map<String, Integer> columns,
                                 Map<String, Department> departmentCache, boolean updateExisting) {
        String code = CsvSupport.require(cells, columns, "lab_code").toUpperCase();
        String name = CsvSupport.require(cells, columns, "lab_name");
        String departmentCode = CsvSupport.require(cells, columns, "department_code");
        int capacity = CsvSupport.parseInt(CsvSupport.require(cells, columns, "capacity"), "capacity");
        String labType = CsvSupport.require(cells, columns, "lab_type");
        String location = CsvSupport.value(cells, columns, "location");
        String statusRaw = CsvSupport.value(cells, columns, "status");

        Department department = departmentCache.computeIfAbsent(departmentCode.toUpperCase(),
                dc -> departmentRepository.findByCode(dc)
                        .orElseThrow(() -> ApiException.validation("Unknown department code '" + dc + "'.")));

        var existing = labRepository.findByLabCode(code);
        Laboratory lab;
        boolean isNew = existing.isEmpty();
        if (isNew) {
            lab = new Laboratory();
        } else if (updateExisting) {
            lab = existing.get();
        } else {
            return RowOutcome.SKIPPED_DUPLICATE;
        }

        lab.setLabCode(code);
        lab.setLabName(name);
        lab.setDepartment(department);
        lab.setCapacity(capacity);
        lab.setLabType(labType);
        lab.setLocation(location);
        if (statusRaw != null) {
            lab.setStatus(parseStatus(statusRaw));
        } else if (isNew) {
            lab.setStatus(LabStatus.ACTIVE);
        }
        labRepository.save(lab);
        return isNew ? RowOutcome.INSERTED : RowOutcome.UPDATED;
    }

    private static LabStatus parseStatus(String raw) {
        try {
            return LabStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.validation("Column 'status' is not a valid laboratory status: '" + raw + "'.");
        }
    }

    private void apply(Laboratory lab, LabRequest request) {
        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> ApiException.notFound("Department", request.departmentId()));
        lab.setLabCode(request.labCode().trim().toUpperCase());
        lab.setLabName(request.labName().trim());
        lab.setDepartment(department);
        lab.setCapacity(request.capacity());
        lab.setLabType(request.labType().trim());
        lab.setLocation(request.location());
        lab.setStatus(request.status() == null ? LabStatus.ACTIVE : request.status());
    }

    private Laboratory find(Long id) {
        return labRepository.findById(id).orElseThrow(() -> ApiException.notFound("Laboratory", id));
    }

    private static String describe(Laboratory lab) {
        return lab.getLabCode() + " | " + lab.getLabName() + " | cap " + lab.getCapacity()
                + " | " + lab.getLabType() + " | " + lab.getStatus();
    }
}
