package com.forge.backend.scheduler.strategy;

import com.forge.backend.model.SchedulingAlgorithm;
import com.forge.backend.model.Task;
import com.forge.backend.model.Worker;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Priority-Based (non-preemptive) scheduling strategy.
 * <p>
 * Selects the task with the highest {@code priority} value (higher number =
 * higher urgency). Ties are resolved by arrival time (earlier task wins),
 * which prevents indefinite starvation of same-priority tasks and degrades
 * gracefully to FCFS ordering when priorities are uniform.
 * <p>
 * Characteristics:
 * <ul>
 *   <li>Non-preemptive</li>
 *   <li>Useful for time-sensitive or SLA-bound workloads</li>
 *   <li>Risk of starvation for low-priority tasks in high-load scenarios
 *       (mitigated in Phase 2 via aging in the C++ engine)</li>
 * </ul>
 */
@Component
public class PriorityScheduler implements SchedulerStrategy {

    @Override
    public Optional<Task> selectNextTask(List<Task> queue, Worker worker) {
        if (queue == null || queue.isEmpty()) {
            return Optional.empty();
        }
        return queue.stream()
                .max(Comparator
                        .comparingInt(Task::getPriority)
                        .thenComparing(Comparator.comparing(Task::getArrivalTime).reversed()));
    }

    @Override
    public SchedulingAlgorithm algorithmType() {
        return SchedulingAlgorithm.PRIORITY;
    }
}
