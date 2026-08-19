package edu.batchmaker;

import edu.batchmaker.config.BatchmakerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableConfigurationProperties(BatchmakerProperties.class)
@EnableJpaAuditing
public class BatchmakerApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchmakerApplication.class, args);
    }
}
