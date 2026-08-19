package edu.batchmaker.security;

import edu.batchmaker.config.BatchmakerProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Issues and verifies HS256 access tokens. */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final BatchmakerProperties properties;
    private SecretKey signingKey;

    @PostConstruct
    void init() {
        String secret = properties.getSecurity().getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "batchmaker.security.jwt.secret must be set and at least 32 bytes long.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(AppUserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.getSecurity().getJwt().getAccessTokenMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(principal.getUsername())
                .issuer(properties.getSecurity().getJwt().getIssuer())
                .claim("uid", principal.getId())
                .claim("role", principal.getRoleName().name())
                .claim("name", principal.getFullName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public Instant expiryOf(String token) {
        return parse(token).map(c -> c.getExpiration().toInstant()).orElse(null);
    }

    public Optional<String> extractUsername(String token) {
        return parse(token).map(Claims::getSubject);
    }

    /** Returns empty for any token that is malformed, tampered with or expired. */
    private Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.getSecurity().getJwt().getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public long getExpiresInSeconds() {
        return properties.getSecurity().getJwt().getAccessTokenMinutes() * 60;
    }
}
