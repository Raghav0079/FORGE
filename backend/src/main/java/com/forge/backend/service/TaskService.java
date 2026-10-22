package com.forge.backend.service;

import com.forge.backend.dto.TaskCreateDTO;
import com.forge.backend.dto.TaskExecutionResponseDTO;
import com.forge.backend.dto.TaskResponseDTO;
import com.forge.backend.model.SchedulingAlgorithm;

import java.util.List;
import java.util.UUID;

/**
 * Business logic contract for the Task lifecycle in FORGE.
 * <p>
 * State machine:
 * <pre>
 *   CREATED ──► QUEUED ──► RUNNING ──► COMPLETED
 *                  │                 └──► FAILED
 *                  └────────────────────► CANCELLED
 * </pre>
 * Terminal states: {@code COMPLETED}, {@code FAILED}, {@code CANCELLED}.
 */
public interface TaskService {

    // -----------------------------------------------------------------------
    // Submission & Read
    // -----------------------------------------------------------------------

    /**
     * Submit a new task. Sets status to CREATED and immediately advances to QUEUED.
     *
     * @param createDTO Validated task payload.
     * @return Persisted task projection.
     */
    TaskResponseDTO submitTask(TaskCreateDTO createDTO);

    /** Retrieve a single task by primary key. */
    TaskResponseDTO getTaskById(UUID id);

    /** Retrieve all tasks across all statuses. */
    List<TaskResponseDTO> getAllTasks();

    /** Retrieve all tasks in a given status. */
    List<TaskResponseDTO> getTasksByStatus(String status);

    // -----------------------------------------------------------------------
    // Lifecycle Transitions
    // -----------------------------------------------------------------------

    /**
     * Advance a CREATED task to QUEUED.
     * Valid from: CREATED.
     */
    TaskResponseDTO queueTask(UUID taskId);

    /**
     * Mark a QUEUED task as RUNNING.
     * Creates a {@code TaskExecution} audit record with the worker id and start time.
     * Valid from: QUEUED.
     *
     * @param taskId   The task being dispatched.
     * @param workerId The worker that will execute it.
     */
    TaskResponseDTO startTask(UUID taskId, UUID workerId);

    /**
     * Mark a RUNNING task as COMPLETED.
     * Closes the {@code TaskExecution} record with end time and computed metrics.
     * Valid from: RUNNING.
     */
    TaskResponseDTO completeTask(UUID taskId);

    /**
     * Mark a RUNNING task as FAILED with an error message.
     * Closes the {@code TaskExecution} record with failure details.
     * Valid from: RUNNING.
     *
     * @param taskId       The failing task.
     * @param errorMessage Description of the failure.
     */
    TaskResponseDTO failTask(UUID taskId, String errorMessage);

    /**
     * Cancel a task. Cancellable from: CREATED, QUEUED.
     * Running tasks cannot be cancelled at the Java layer (requires C++ engine
     * preemption, Phase 2).
     */
    TaskResponseDTO cancelTask(UUID taskId);

    // -----------------------------------------------------------------------
    // Scheduling Dispatch
    // -----------------------------------------------------------------------

    /**
     * Select the next QUEUED task for a given worker using the configured algorithm.
     * Does NOT change task state; call {@link #startTask} after selection.
     *
     * @param algorithm Scheduling algorithm to apply.
     * @param workerId  Worker requesting work.
     * @return The selected task projection, or empty if the queue is empty.
     */
    java.util.Optional<TaskResponseDTO> selectNextTask(SchedulingAlgorithm algorithm, UUID workerId);

    // -----------------------------------------------------------------------
    // Audit / Execution History
    // -----------------------------------------------------------------------

    /** Retrieve all execution records for a given task. */
    List<TaskExecutionResponseDTO> getExecutionHistory(UUID taskId);
}
