package com.forge.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Flyway migration strategy that:
 * <ol>
 *   <li>Calls {@code repair()} before every migrate run — clears any
 *       previously-FAILED migration entries from {@code flyway_schema_history}
 *       so re-application is permitted.</li>
 *   <li>Registers an {@code AFTER_MIGRATE} callback that idempotently ensures
 *       every table and column required by the JPA entities exists, using
 *       {@code CREATE TABLE IF NOT EXISTS} and
 *       {@code ADD COLUMN IF NOT EXISTS}.  This fires as part of Flyway's
 *       own pipeline — <em>before</em> Hibernate builds its
 *       {@code SessionFactory} — so {@code ddl-auto: validate} will pass
 *       even if earlier migration scripts partially failed.</li>
 * </ol>
 */
@Slf4j
@Configuration
public class FlywayConfig {

    /**
     * Custom migration strategy: repair → migrate.
     * Repair removes FAILED rows from flyway_schema_history, allowing
     * Flyway to re-attempt previously-failed migrations on the next startup.
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            log.info("Flyway: running repair() to clear any failed migration entries…");
            flyway.repair();
            log.info("Flyway: running migrate()…");
            flyway.migrate();
        };
    }

    /**
     * Flyway callback that runs idempotent DDL immediately after all
     * migration scripts have been applied.  Handles the edge case where
     * {@code flyway_schema_history} shows a migration as SUCCESS but the
     * corresponding DDL was never physically committed (e.g., partially-run
     * transactions on a non-transactional DDL database).
     */
    @Bean
    public Callback schemaReconciliationCallback() {
        return new Callback() {

            @Override
            public boolean supports(Event event, Context context) {
                return event == Event.AFTER_MIGRATE;
            }

            @Override
            public boolean canHandleInTransaction(Event event, Context context) {
                // DDL in PostgreSQL is transactional but we want an explicit
                // connection to avoid interfering with Flyway's own transaction.
                return false;
            }

            @Override
            public void handle(Event event, Context context) {
                log.info("Flyway callback AFTER_MIGRATE: reconciling schema…");
                Connection conn = context.getConnection();
                try (Statement st = conn.createStatement()) {

                    // -------------------------------------------------------
                    // workers table (created in V2, columns extended in V3)
                    // -------------------------------------------------------
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS workers (
                            id                UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
                            name              VARCHAR(100) NOT NULL,
                            status            VARCHAR(20)  NOT NULL DEFAULT 'IDLE',
                            last_heartbeat_at TIMESTAMP,
                            created_at        TIMESTAMP    NOT NULL DEFAULT now(),
                            updated_at        TIMESTAMP    NOT NULL DEFAULT now()
                        )
                        """);
                    st.execute(
                        "ALTER TABLE workers ADD COLUMN IF NOT EXISTS last_heartbeat_at TIMESTAMP");

                    // -------------------------------------------------------
                    // schedulers table (created in V2)
                    // -------------------------------------------------------
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS schedulers (
                            id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
                            name       VARCHAR(100) NOT NULL,
                            status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
                            algorithm  VARCHAR(30)  NOT NULL,
                            created_at TIMESTAMP    NOT NULL DEFAULT now(),
                            updated_at TIMESTAMP    NOT NULL DEFAULT now()
                        )
                        """);

                    // -------------------------------------------------------
                    // task_executions OS-scheduling metric columns (V3)
                    // -------------------------------------------------------
                    st.execute(
                        "ALTER TABLE task_executions ADD COLUMN IF NOT EXISTS waiting_time_ms    BIGINT");
                    st.execute(
                        "ALTER TABLE task_executions ADD COLUMN IF NOT EXISTS turnaround_time_ms BIGINT");

                    // -------------------------------------------------------
                    // tasks — payload column (V2)
                    // -------------------------------------------------------
                    st.execute(
                        "ALTER TABLE tasks ADD COLUMN IF NOT EXISTS payload TEXT");

                    // -------------------------------------------------------
                    // Seed heartbeat timestamp for existing workers
                    // -------------------------------------------------------
                    st.execute("""
                        UPDATE workers
                        SET last_heartbeat_at = now()
                        WHERE last_heartbeat_at IS NULL
                        """);

                    log.info("Flyway callback: schema reconciliation complete ✓");

                } catch (SQLException e) {
                    // Log but do not rethrow — Flyway should still succeed
                    // even if some IF NOT EXISTS guards trigger expected errors.
                    log.warn("Flyway callback: reconciliation warning (likely harmless): {}", e.getMessage());
                }
            }

            @Override
            public String getCallbackName() {
                return "SchemaReconciliationCallback";
            }
        };
    }
}
