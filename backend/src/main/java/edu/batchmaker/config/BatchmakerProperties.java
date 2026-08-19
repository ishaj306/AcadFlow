package edu.batchmaker.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly typed view over the {@code batchmaker.*} configuration block.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "batchmaker")
public class BatchmakerProperties {

    private Security security = new Security();
    private Solver solver = new Solver();
    private Cors cors = new Cors();
    private Bootstrap bootstrap = new Bootstrap();

    @Getter
    @Setter
    public static class Security {
        private Jwt jwt = new Jwt();
    }

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private String issuer = "batchmaker";
        private long accessTokenMinutes = 480;
    }

    @Getter
    @Setter
    public static class Solver {
        private String baseUrl = "http://127.0.0.1:8090";
        private int connectTimeoutSeconds = 10;
        private int readTimeoutSeconds = 180;
        private boolean fallbackEnabled = true;
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
    }

    /**
     * The single administrator account created on an empty database. No other
     * data is ever created by the system.
     */
    @Getter
    @Setter
    public static class Bootstrap {
        private String adminUsername = "admin";
        private String adminFullName = "System Administrator";
        private String adminEmail = "admin@localhost";
        /** Left blank, a strong password is generated and logged once at startup. */
        private String adminPassword;
    }
}
