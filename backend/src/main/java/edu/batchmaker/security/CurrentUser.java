package edu.batchmaker.security;

import edu.batchmaker.domain.entity.User;
import edu.batchmaker.exception.ApiException;
import edu.batchmaker.exception.ErrorCode;
import edu.batchmaker.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Convenience accessor for the authenticated principal inside services. */
@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final UserRepository userRepository;

    public Optional<AppUserPrincipal> principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    public AppUserPrincipal requirePrincipal() {
        return principal().orElseThrow(() ->
                new ApiException(ErrorCode.UNAUTHENTICATED, "No authenticated user in context."));
    }

    public Optional<Long> userId() {
        return principal().map(AppUserPrincipal::getId);
    }

    /** Loads the full user row; use sparingly, prefer {@link #userId()}. */
    public Optional<User> entity() {
        return userId().flatMap(userRepository::findById);
    }
}
