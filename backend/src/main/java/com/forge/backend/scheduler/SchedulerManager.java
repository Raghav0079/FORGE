package com.forge.backend.scheduler;

import com.forge.backend.model.SchedulingAlgorithm;
import com.forge.backend.model.Task;
import com.forge.backend.model.Worker;
import com.forge.backend.scheduler.strategy.SchedulerStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Central dispatcher that delegates task-selection to the appropriate
 * {@link SchedulerStrategy} implementation at runtime.
 * <p>
 * All concrete strategy beans are auto-collected via Spring's constructor
 * injection of {@code List<SchedulerStrategy>}. The manager builds an
 * {@link EnumMap} keyed by {@link SchedulingAlgorithm} for O(1) lookup.
 * <p>
 * Usage:
 * <pre>
 *   Optional&lt;Task&gt; next = schedulerManager.selectNext(
 *       SchedulingAlgorithm.PRIORITY, queuedTasks, worker);
 * </pre>
 */
@Slf4j
@Component
public class SchedulerManager {

    private final Map<SchedulingAlgorithm, SchedulerStrategy> strategyMap;

    /**
     * Spring injects all {@link SchedulerStrategy} beans discovered on the
     * classpath. Adding a new algorithm only requires implementing the interface
     * and annotating with {@code @Component} — zero changes here.
     */
    public SchedulerManager(List<SchedulerStrategy> strategies) {
        strategyMap = new EnumMap<>(SchedulingAlgorithm.class);
        for (SchedulerStrategy strategy : strategies) {
            strategyMap.put(strategy.algorithmType(), strategy);
            log.info("Registered scheduling strategy: {}", strategy.algorithmType());
        }
    }

    /**
     * Select the next task to dispatch using the specified algorithm.
     *
     * @param algorithm The scheduling algorithm to apply.
     * @param queue     Snapshot of QUEUED tasks available for dispatch.
     * @param worker    The worker requesting a task assignment.
     * @return The selected task wrapped in an {@link Optional}, or empty if
     *         the queue is empty or no strategy is registered.
     */
    public Optional<Task> selectNext(
            SchedulingAlgorithm algorithm,
            List<Task> queue,
            Worker worker) {

        SchedulerStrategy strategy = strategyMap.get(algorithm);

        if (strategy == null) {
            log.warn("No strategy registered for algorithm [{}]. Falling back to FCFS.", algorithm);
            strategy = strategyMap.get(SchedulingAlgorithm.FCFS);
        }

        if (strategy == null) {
            log.error("FCFS strategy is not registered — cannot select a task.");
            return Optional.empty();
        }

        Optional<Task> selected = strategy.selectNextTask(queue, worker);
        selected.ifPresentOrElse(
                t -> log.debug("Strategy [{}] selected task [{}] for worker [{}]",
                        algorithm, t.getId(), worker.getId()),
                () -> log.debug("Strategy [{}] returned no task for worker [{}] (queue size: {})",
                        algorithm, worker.getId(), queue.size())
        );
        return selected;
    }

    /**
     * Returns the set of algorithms for which strategies are currently registered.
     * Exposed for health-check and monitoring endpoints.
     */
    public java.util.Set<SchedulingAlgorithm> registeredAlgorithms() {
        return strategyMap.keySet();
    }
}
