package com.forge.backend.service;

import com.forge.backend.dto.WorkerCreateDTO;
import com.forge.backend.dto.WorkerResponseDTO;

import java.util.List;
import java.util.UUID;

/**
 * Business logic contract for worker node lifecycle management.
 */
public interface WorkerService {

    /** Register a new worker node; initial status is IDLE. */
    WorkerResponseDTO registerWorker(WorkerCreateDTO createDTO);

    /** Retrieve a single worker by id. */
    WorkerResponseDTO getWorkerById(UUID id);

    /** Retrieve all workers, optionally filtered by status in the controller layer. */
    List<WorkerResponseDTO> getAllWorkers();

    /** Retrieve only workers in IDLE status (used by the dispatch loop). */
    List<WorkerResponseDTO> getIdleWorkers();

    /**
     * Record a heartbeat from the worker, resetting its liveness timer.
     * If the worker was OFFLINE, it is restored to IDLE.
     *
     * @param workerId The reporting worker's id.
     * @return Updated worker projection.
     */
    WorkerResponseDTO heartbeat(UUID workerId);

    /**
     * Mark a worker as BUSY (called by TaskServiceImpl when dispatching a task).
     *
     * @param workerId The worker to mark busy.
     */
    void markBusy(UUID workerId);

    /**
     * Mark a worker as IDLE (called after task completion/failure).
     *
     * @param workerId The worker to return to idle pool.
     */
    void markIdle(UUID workerId);

    /**
     * Scheduled heartbeat check: marks stale workers OFFLINE and re-queues
     * any tasks they were running. Called by {@code @Scheduled} in the impl.
     */
    void checkAndMarkOfflineWorkers();
}
