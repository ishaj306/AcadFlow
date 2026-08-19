package edu.batchmaker.service;

import edu.batchmaker.domain.enums.LabStatus;
import edu.batchmaker.domain.enums.RecordStatus;
import edu.batchmaker.domain.enums.TimetableStatus;
import edu.batchmaker.dto.common.SetupStatusResponse;
import edu.batchmaker.repository.*;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reports how far an installation has been set up.
 *
 * <p>A fresh database contains only roles and one administrator, so every screen
 * would otherwise be empty with no indication of what to do first. The steps are
 * listed in dependency order, and each reports the concrete reason it is not yet
 * satisfied.
 */
@Service
@RequiredArgsConstructor
public class SetupService {

    private final DepartmentRepository departmentRepository;
    private final AcademicTermRepository termRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final WorkingDayRepository workingDayRepository;
    private final SubjectRepository subjectRepository;
    private final LaboratoryRepository labRepository;
    private final FacultyRepository facultyRepository;
    private final FacultySubjectRepository facultySubjectRepository;
    private final StudentRepository studentRepository;
    private final StudentBatchRepository batchRepository;
    private final TimetableRepository timetableRepository;

    @Transactional(readOnly = true)
    public SetupStatusResponse status() {
        List<SetupStatusResponse.Step> steps = new ArrayList<>();

        long departments = departmentRepository.count();
        steps.add(step("departments", "Add a department",
                "Every subject, laboratory, faculty member and student belongs to one.",
                "/settings", departments > 0, departments,
                "No department has been created yet."));

        boolean hasCurrentTerm = termRepository.findByCurrentTrue().isPresent();
        long terms = termRepository.count();
        steps.add(step("term", "Set the academic term",
                "Scheduling always runs against the current term.",
                "/settings", hasCurrentTerm, terms,
                terms == 0 ? "No academic term exists yet."
                        : "A term exists but none is marked as current."));

        long teachingSlots = timeSlotRepository.findSchedulableSlots().size();
        long workingDays = workingDayRepository.findByActiveTrueOrderByDayOrderAsc().size();
        boolean hoursReady = teachingSlots > 0 && workingDays > 0;
        steps.add(step("hours", "Define working hours",
                "The periods of the college day and the days practicals may run.",
                "/settings", hoursReady, teachingSlots,
                teachingSlots == 0 ? "No teaching periods have been configured."
                        : "No working days are active."));

        long practicalSubjects = subjectRepository.findSchedulablePracticalSubjects().size();
        steps.add(step("subjects", "Add practical subjects",
                "Duration, sessions per week, batch size and the laboratory type required.",
                "/subjects", practicalSubjects > 0, practicalSubjects,
                "No practical subjects have been added."));

        long activeLabs = labRepository.countByStatus(LabStatus.ACTIVE);
        steps.add(step("labs", "Add laboratories",
                "Capacity and type determine which batches can be hosted where.",
                "/laboratories", activeLabs > 0, activeLabs,
                "No active laboratory has been added."));

        long activeFaculty = facultyRepository.countByStatus(RecordStatus.ACTIVE);
        // Faculty without a subject qualification can never be scheduled.
        long qualified = facultySubjectRepository.findAll().stream()
                .map(link -> link.getFaculty().getId())
                .distinct()
                .count();
        boolean facultyReady = activeFaculty > 0 && qualified > 0;
        steps.add(step("faculty", "Add faculty and their subjects",
                "The optimiser will not assign a subject to anyone not listed as qualified for it.",
                "/faculty", facultyReady, activeFaculty,
                activeFaculty == 0 ? "No active faculty have been added."
                        : "Faculty exist but none is qualified for any subject."));

        long students = studentRepository.countByStatus(RecordStatus.ACTIVE);
        steps.add(step("students", "Add students",
                "Enter them individually or import a roll list as CSV.",
                "/students", students > 0, students,
                "No active students have been added."));

        long batches = batchRepository.countByStatus(RecordStatus.ACTIVE);
        steps.add(step("batches", "Generate practical batches",
                "Splits each division into evenly sized batches that fit a laboratory.",
                "/batches", batches > 0, batches,
                "No practical batches have been generated."));

        long published = timetableRepository.countByStatus(TimetableStatus.PUBLISHED);
        steps.add(step("timetable", "Generate and publish the timetable",
                "Runs the optimiser, validates the result, then publishes on approval.",
                "/timetable", published > 0, published,
                "No timetable has been published."));

        int complete = (int) steps.stream().filter(SetupStatusResponse.Step::complete).count();
        return new SetupStatusResponse(complete == steps.size(), complete, steps.size(), steps);
    }

    private static SetupStatusResponse.Step step(String key, String title, String description,
                                                 String route, boolean complete, long count,
                                                 String blocker) {
        return new SetupStatusResponse.Step(
                key, title, description, route, complete, count, complete ? null : blocker);
    }
}
