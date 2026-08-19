package edu.batchmaker.seed;

import edu.batchmaker.config.BatchmakerProperties;
import edu.batchmaker.domain.entity.Role;
import edu.batchmaker.domain.entity.User;
import edu.batchmaker.domain.enums.RecordStatus;
import edu.batchmaker.domain.enums.RoleName;
import edu.batchmaker.repository.RoleRepository;
import edu.batchmaker.repository.UserRepository;
import java.security.SecureRandom;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the minimum needed to sign in to an empty installation: the four
 * roles, and one administrator account.
 *
 * <p>Deliberately creates no departments, subjects, laboratories, faculty or
 * students. All institutional data is entered by the administrator through the
 * application, so nothing in the database is invented by the system.
 *
 * <p>The administrator password comes from
 * {@code batchmaker.bootstrap.admin-password}. If that is not set, a random one
 * is generated and printed once to the startup log - so an installation is never
 * left with a guessable default.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class BootstrapRunner implements ApplicationRunner {

    private static final String PASSWORD_ALPHABET =
            "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BatchmakerProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureRoles();
        ensureAdministrator();
    }

    /** Roles are fixed by the authorisation model, so they are created if absent. */
    private void ensureRoles() {
        record RoleSeed(RoleName name, String description) {
        }
        List<RoleSeed> required = List.of(
                new RoleSeed(RoleName.ADMIN, "Full administrative access to every module"),
                new RoleSeed(RoleName.HOD, "Department head and timetable coordinator"),
                new RoleSeed(RoleName.FACULTY, "Teaching staff"),
                new RoleSeed(RoleName.STUDENT, "Enrolled student"));

        for (RoleSeed seed : required) {
            if (roleRepository.findByName(seed.name()).isPresent()) {
                continue;
            }
            Role role = new Role();
            role.setName(seed.name());
            role.setDescription(seed.description());
            roleRepository.save(role);
            log.info("Created role {}", seed.name());
        }
    }

    private void ensureAdministrator() {
        String username = properties.getBootstrap().getAdminUsername();

        if (userRepository.existsByUsername(username)) {
            return;
        }
        // Any existing administrator means this installation is already set up.
        if (!userRepository.findByRoleName(RoleName.ADMIN).isEmpty()) {
            return;
        }

        Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role missing after bootstrap."));

        String configured = properties.getBootstrap().getAdminPassword();
        boolean generated = configured == null || configured.isBlank();
        String password = generated ? randomPassword() : configured;

        User admin = new User();
        admin.setUsername(username);
        admin.setEmail(properties.getBootstrap().getAdminEmail());
        admin.setFullName(properties.getBootstrap().getAdminFullName());
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(adminRole);
        admin.setStatus(RecordStatus.ACTIVE);
        userRepository.save(admin);

        log.info("");
        log.info("================================================================");
        log.info("  Empty database detected - created the administrator account.");
        log.info("");
        log.info("     username : {}", username);
        if (generated) {
            log.info("     password : {}", password);
            log.info("");
            log.info("  This password was generated and is shown only once.");
            log.info("  Set batchmaker.bootstrap.admin-password to choose your own.");
        } else {
            log.info("     password : (as configured)");
        }
        log.info("");
        log.info("  Sign in and use Settings to add departments, terms and working");
        log.info("  hours, then add subjects, laboratories, faculty and students.");
        log.info("================================================================");
        log.info("");
    }

    private static String randomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            password.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        return password.toString();
    }
}
