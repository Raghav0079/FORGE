package com.forge.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activates Spring's {@code @Scheduled} infrastructure.
 * Required for {@link com.forge.backend.service.impl.WorkerServiceImpl#checkAndMarkOfflineWorkers()}.
 *
 * <p>Scheduling parameters are externalised to {@code application.yml}:
 * <pre>
 * forge:
 *   worker:
 *     heartbeat:
 *       timeout-seconds: 120
 *       check-interval-ms: 60000
 *   scheduler:
 *     round-robin:
 *       quantum-ms: 500
 * </pre>
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
    // Marker class — all configuration is annotation-driven.
}
