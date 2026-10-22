package com.forge.backend.scheduler.strategy;

import com.forge.backend.model.Task;
import com.forge.backend.model.Worker;

import java.util.List;
import java.util.Optional;

/**
 * Strategy interface for all task scheduling algorithms in FORGE.
 * <p>
 * Each implementation selects the next {@link Task} to dispatch
 * from a ready queue of QUEUED tasks, given the requesting {@link Worker}.
 * Returning {@link Optional#empty()} signals that no eligible task was found
 * (e.g., the queue is empty or no task matches the algorithm criteria).
 * <p>
 * Implementations MUST be stateless and thread-safe: {@link SchedulerManager}
 * may call {@code selectNextTask} concurrently from multiple worker threads.
 */
public interface SchedulerStrategy {

    /**
     * Select the highest-priority task to execute next.
     *
     * @param queue  Ordered snapshot of QUEUED tasks available for dispatch.
     *               The list is a defensive copy; modifications will not affect
     *               the backing store.
     * @param worker The worker requesting work. Implementations may inspect
     *               worker metadata (e.g., capabilities) for advanced routing.
     * @return An {@link Optional} wrapping the chosen task, or empty if none.
     */
    Optional<Task> selectNextTask(List<Task> queue, Worker worker);

    /**
     * The {@link com.forge.backend.model.SchedulingAlgorithm} enum constant
     * this strategy implements. Used by {@link SchedulerManager} for dispatch.
     *
     * @return canonical algorithm identifier.
     */
    com.forge.backend.model.SchedulingAlgorithm algorithmType();
}
