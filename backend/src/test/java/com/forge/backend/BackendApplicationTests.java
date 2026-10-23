package com.forge.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Application context smoke test.
 * Uses the "test" profile which disables Flyway and sets ddl-auto=none
 * to avoid requiring a live PostgreSQL database during CI builds.
 */
@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring ApplicationContext starts without errors.
    }
}
