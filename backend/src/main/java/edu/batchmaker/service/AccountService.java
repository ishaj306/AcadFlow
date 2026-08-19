package edu.batchmaker.service;

import edu.batchmaker.domain.entity.Faculty;
import edu.batchmaker.domain.entity.Role;
import edu.batchmaker.domain.entity.Student;
import edu.batchmaker.domain.entity.User;
import edu.batchmaker.domain.enums.RecordStatus;
import edu.batchmaker.domain.enums.RoleName;
import edu.batchmaker.dto.common.AccountRequest;
import edu.batchmaker.dto.common.AccountResponse;
import edu.batchmaker.exception.ApiException;
import edu.batchmaker.exception.ErrorCode;
import edu.batchmaker.repository.FacultyRepository;
import edu.batchmaker.repository.RoleRepository;
import edu.batchmaker.repository.StudentRepository;
import edu.batchmaker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates sign-in accounts for people already on record.
 *
 * <p>A faculty member or student exists as institutional data first; giving them
 * a login is a separate, explicit step, so importing a roll list never silently
 * creates hundreds of credentials.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final FacultyRepository facultyRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional
    public AccountResponse createForFaculty(Long facultyId, AccountRequest request, RoleName role) {
        Faculty faculty = facultyRepository.findById(facultyId)
                .orElseThrow(() -> ApiException.notFound("Faculty", facultyId));

        if (faculty.getUser() != null) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE,
                    faculty.getName() + " already has the sign-in account '"
                            + faculty.getUser().getUsername() + "'.");
        }
        if (role != RoleName.FACULTY && role != RoleName.HOD) {
            throw ApiException.validation("A faculty account must have the FACULTY or HOD role.");
        }

        String username = defaultUsername(request.username(), faculty.getEmployeeCode());
        User user = create(username, faculty.getEmail(), faculty.getName(), request.password(), role);
        user.setDepartment(faculty.getDepartment());
        faculty.setUser(user);

        auditService.record("CREATE_ACCOUNT", "Faculty", facultyId, null, username + " as " + role);
        return new AccountResponse(user.getId(), user.getUsername(), role, user.getFullName());
    }

    @Transactional
    public AccountResponse createForStudent(Long studentId, AccountRequest request) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> ApiException.notFound("Student", studentId));

        if (student.getUser() != null) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE,
                    student.getName() + " already has the sign-in account '"
                            + student.getUser().getUsername() + "'.");
        }

        String username = defaultUsername(request.username(), student.getRollNumber());
        User user = create(username, student.getEmail(), student.getName(),
                request.password(), RoleName.STUDENT);
        user.setDepartment(student.getDepartment());
        student.setUser(user);

        auditService.record("CREATE_ACCOUNT", "Student", studentId, null, username);
        return new AccountResponse(user.getId(), user.getUsername(), RoleName.STUDENT, user.getFullName());
    }

    /** Resets the password of an existing account. */
    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User", userId));
        if (newPassword == null || newPassword.length() < 8) {
            throw ApiException.validation("The password must be at least 8 characters.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        auditService.record("RESET_PASSWORD", "User", userId);
    }

    private User create(String username, String email, String fullName,
                        String password, RoleName roleName) {
        if (userRepository.existsByUsername(username)) {
            throw ApiException.duplicate("The username '" + username + "' is already taken.");
        }
        if (userRepository.existsByEmail(email)) {
            throw ApiException.duplicate("An account already uses the email '" + email + "'.");
        }
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ApiException(ErrorCode.ILLEGAL_STATE,
                        "Role " + roleName + " is missing."));

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setStatus(RecordStatus.ACTIVE);
        return userRepository.save(user);
    }

    private static String defaultUsername(String requested, String fallback) {
        String username = requested == null || requested.isBlank() ? fallback : requested;
        return username.trim().toLowerCase();
    }
}
