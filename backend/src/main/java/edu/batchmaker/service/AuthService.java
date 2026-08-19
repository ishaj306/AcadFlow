package edu.batchmaker.service;

import edu.batchmaker.domain.entity.User;
import edu.batchmaker.dto.auth.CurrentUserResponse;
import edu.batchmaker.dto.auth.LoginRequest;
import edu.batchmaker.dto.auth.LoginResponse;
import edu.batchmaker.exception.ApiException;
import edu.batchmaker.exception.ErrorCode;
import edu.batchmaker.repository.FacultyRepository;
import edu.batchmaker.repository.StudentRepository;
import edu.batchmaker.repository.UserRepository;
import edu.batchmaker.security.AppUserPrincipal;
import edu.batchmaker.security.CurrentUser;
import edu.batchmaker.security.JwtService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final FacultyRepository facultyRepository;
    private final StudentRepository studentRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        AppUserPrincipal principal;
        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
            principal = (AppUserPrincipal) authentication.getPrincipal();
        } catch (DisabledException ex) {
            throw new ApiException(ErrorCode.ACCOUNT_INACTIVE,
                    "This account has been deactivated. Contact the administrator.");
        } catch (AuthenticationException ex) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid username or password.");
        }

        userRepository.findById(principal.getId()).ifPresent(user -> user.setLastLoginAt(Instant.now()));
        auditService.record("LOGIN", "User", principal.getId());

        String token = jwtService.generateToken(principal);
        return LoginResponse.of(token, jwtService.getExpiresInSeconds(), describe(principal.getId()));
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse me() {
        return describe(currentUser.requirePrincipal().getId());
    }

    /** Resolves the linked faculty/student record so the UI can scope its views. */
    private CurrentUserResponse describe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User", userId));

        Long facultyId = facultyRepository.findByUserId(userId).map(f -> f.getId()).orElse(null);
        Long studentId = studentRepository.findByUserId(userId).map(s -> s.getId()).orElse(null);

        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().getName(),
                user.getDepartment() == null ? null : user.getDepartment().getId(),
                user.getDepartment() == null ? null : user.getDepartment().getName(),
                facultyId,
                studentId);
    }
}
