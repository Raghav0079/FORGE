package com.forge.backend.scheduler.strategy;

import com.forge.backend.model.SchedulingAlgorithm;
import com.forge.backend.model.Task;
import com.forge.backend.model.Worker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round-Robin (RR) scheduling strategy with configurable time quantum.
 * <p>
 * Maintains a per-algorithm, in-memory pointer to the last-dispatched task
 * position so that successive calls cycle through the queue in insertion order
 * (arrival time). Each call advances the pointer by one.
 * <p>
 * <strong>Important:</strong> The actual preemption and time-quantum enforcement
 * happens in the C++ execution engine (Phase 2). At the Java scheduling layer,
 * RR simply selects tasks in circular order; the engine is responsible for
 * interrupting execution after {@code quantum} milliseconds and re-queuing the
 * remainder.
 * <p>
 * Characteristics:
 * <ul>
 *   <li>Preemptive (requires engine cooperation for quantum enforcement)</li>
 *   <li>Fair CPU distribution across all QUEUED tasks</li>
 *   <li>Higher context-switch overhead than FCFS/SJF</li>
 * </ul>
 */
@Component
public class RoundRobinScheduler implements SchedulerStrategy {

    /**
     * Time quantum in milliseconds. Injected from application config.
     * The C++ engine will preempt any task that runs beyond this threshold.
     */
    @Value("${forge.scheduler.round-robin.quantum-ms:500}")
    private long quantumMs;

    /**
     * Pointer per-worker tracking which position in the sorted queue to pick next.
     * Key: worker UUID string. Value: current circular index.
     */
    private final ConcurrentHashMap<String, AtomicInteger> workerPointers =
            new ConcurrentHashMap<>();

    @Override
    public Optional<Task> selectNextTask(List<Task> queue, Worker worker) {
        if (queue == null || queue.isEmpty()) {
            return Optional.empty();
        }

        // Sort by arrival time for a deterministic circular order.
        List<Task> sorted = queue.stream()
                .sorted(Comparator.comparing(Task::getArrivalTime))
                .toList();

        String workerKey = worker.getId().toString();
        AtomicInteger pointer = workerPointers.computeIfAbsent(workerKey,
                k -> new AtomicInteger(0));

        // Wrap around: index mod queue size.
        int index = pointer.getAndUpdate(i -> (i + 1) % sorted.size());
        // Guard: if pointer jumped past current queue size (queue shrunk), reset.
        if (index >= sorted.size()) {
            pointer.set(0);
            index = 0;
        }

        return Optional.of(sorted.get(index));
    }

    @Override
    public SchedulingAlgorithm algorithmType() {
        return SchedulingAlgorithm.ROUND_ROBIN;
    }

    /** Exposed for testing and monitoring. */
    public long getQuantumMs() {
        return quantumMs;
    }
}
