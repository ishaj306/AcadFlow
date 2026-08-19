package edu.batchmaker.service.solver;

import edu.batchmaker.config.BatchmakerProperties;
import edu.batchmaker.dto.solver.SolverPayload;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Thin REST client for the CP-SAT optimisation service. */
@Slf4j
@Component
public class SolverClient {

    private final RestClient restClient;
    private final BatchmakerProperties properties;

    public SolverClient(BatchmakerProperties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.getSolver().getConnectTimeoutSeconds()));
        // Solving is deliberately allowed to take a while; the read timeout is
        // the real bound on how long generation may run.
        factory.setReadTimeout(Duration.ofSeconds(properties.getSolver().getReadTimeoutSeconds()));

        this.restClient = RestClient.builder()
                .baseUrl(properties.getSolver().getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    public boolean isAvailable() {
        try {
            restClient.get().uri("/health").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException ex) {
            log.debug("Solver health check failed: {}", ex.getMessage());
            return false;
        }
    }

    /** Full generation. Throws {@link SolverUnavailableException} if unreachable. */
    public SolverPayload.Response solve(SolverPayload.Request request) {
        return post("/solve", request);
    }

    /** Repair of an existing timetable, minimising disruption (S6). */
    public SolverPayload.Response reschedule(SolverPayload.Request request) {
        return post("/reschedule", request);
    }

    /** Fast resource pre-check; no solve. Throws if the service is unreachable. */
    public SolverPayload.FeasibilityReport feasibility(SolverPayload.Request request) {
        try {
            return restClient.post()
                    .uri("/feasibility")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(SolverPayload.FeasibilityReport.class);
        } catch (RestClientException ex) {
            throw new SolverUnavailableException(
                    "The optimisation service at " + properties.getSolver().getBaseUrl()
                            + " could not be reached: " + ex.getMessage(), ex);
        }
    }

    private SolverPayload.Response post(String path, SolverPayload.Request request) {
        try {
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(SolverPayload.Response.class);
        } catch (RestClientException ex) {
            throw new SolverUnavailableException(
                    "The optimisation service at " + properties.getSolver().getBaseUrl()
                            + " could not be reached: " + ex.getMessage(), ex);
        }
    }

    /** Signals that the external engine is down so callers can fall back. */
    public static class SolverUnavailableException extends RuntimeException {
        public SolverUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
