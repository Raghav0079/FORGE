package com.forge.backend.scheduler.strategy;

import com.forge.backend.model.SchedulingAlgorithm;
import com.forge.backend.model.Task;
import com.forge.backend.model.Worker;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Shortest Job First (SJF) — Non-Preemptive scheduling strategy.
 * <p>
 * Selects the task with the smallest declared {@code executionTime}.
 * Ties are broken by arrival time (earlier task wins) to maintain fairness.
 * <p>
 * Characteristics:
 * <ul>
 *   <li>Non-preemptive (once started, a task runs to completion)</li>
 *   <li>Minimises average waiting time across a batch</li>
 *   <li>Can cause starvation for long tasks when short tasks keep arriving</li>
 *   <li>Requires accurate {@code executionTime} estimates from the submitter</li>
 * </ul>
 */
@Component
public class SJFScheduler implements SchedulerStrategy {

    @Override
    public Optional<Task> selectNextTask(List<Task> queue, Worker worker) {
        if (queue == null || queue.isEmpty()) {
            return Optional.empty();
        }
        return queue.stream()
                .min(Comparator
                        .comparingLong(Task::getExecutionTime)
                        .thenComparing(Task::getArrivalTime));
    }

    @Override
    public SchedulingAlgorithm algorithmType() {
        return SchedulingAlgorithm.SJF;
    }
}
