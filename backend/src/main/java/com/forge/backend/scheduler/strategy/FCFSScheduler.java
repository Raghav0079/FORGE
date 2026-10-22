package com.forge.backend.scheduler.strategy;

import com.forge.backend.model.SchedulingAlgorithm;
import com.forge.backend.model.Task;
import com.forge.backend.model.Worker;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * First-Come, First-Served (FCFS) scheduling strategy.
 * <p>
 * Selects the task with the earliest {@code arrivalTime}, modelling a strict
 * FIFO queue. Tasks with equal arrival times are resolved by creation order
 * (UUID insert order is preserved by the database index on
 * {@code (status, arrival_time ASC)}).
 * <p>
 * Characteristics:
 * <ul>
 *   <li>Non-preemptive</li>
 *   <li>Simple and starvation-free</li>
 *   <li>May cause the "convoy effect" (long tasks block short ones)</li>
 * </ul>
 */
@Component
public class FCFSScheduler implements SchedulerStrategy {

    @Override
    public Optional<Task> selectNextTask(List<Task> queue, Worker worker) {
        if (queue == null || queue.isEmpty()) {
            return Optional.empty();
        }
        return queue.stream()
                .min(Comparator.comparing(Task::getArrivalTime));
    }

    @Override
    public SchedulingAlgorithm algorithmType() {
        return SchedulingAlgorithm.FCFS;
    }
}
