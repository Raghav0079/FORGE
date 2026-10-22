package com.forge.backend.service.impl;

import com.forge.backend.dto.WorkerCreateDTO;
import com.forge.backend.dto.WorkerResponseDTO;
import com.forge.backend.exception.ResourceNotFoundException;
import com.forge.backend.model.Task;
import com.forge.backend.model.TaskStatus;
import com.forge.backend.model.Worker;
import com.forge.backend.model.WorkerStatus;
import com.forge.backend.repository.TaskRepository;
import com.forge.backend.repository.WorkerRepository;
import com.forge.backend.service.WorkerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Production implementation of {@link WorkerService}.
 * <p>
 * Heartbeat monitoring strategy:
 * <ol>
 *   <li>Workers call {@code POST /api/workers/{id}/heartbeat} periodically
 *       (recommended every 10–30 s).</li>
 *   <li>The {@code @Scheduled} method {@link #checkAndMarkOfflineWorkers()} runs
 *       every 60 s and marks as OFFLINE any worker whose
 *       {@code lastHeartbeatAt} is older than the configured threshold.</li>
 *   <li>Orphaned RUNNING tasks (assigned to a now-OFFLINE worker) are re-queued
 *       so they are not permanently stuck.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerServiceImpl implements WorkerService {

    private final WorkerRepository workerRepository;
    private final TaskRepository taskRepository;

    /**
     * Maximum age of a heartbeat before a worker is considered offline.
     * Default: 120 seconds (2 minutes). Override via
     * {@code forge.worker.heartbeat.timeout-seconds} in application.yml.
     */
    @Value("${forge.worker.heartbeat.timeout-seconds:120}")
    private long heartbeatTimeoutSeconds;

    // -----------------------------------------------------------------------
    // Registration & Read
    // -----------------------------------------------------------------------

    @Override
    @Transactional
    public WorkerResponseDTO registerWorker(WorkerCreateDTO createDTO) {
        Worker worker = Worker.builder()
                .name(createDTO.name())
                .status(WorkerStatus.IDLE)
                .lastHeartbeatAt(LocalDateTime.now())
                .build();
        return mapToDTO(workerRepository.save(worker));
    }

    @Override
    @Transactional(readOnly = true)
    public WorkerResponseDTO getWorkerById(UUID id) {
        return workerRepository.findById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkerResponseDTO> getAllWorkers() {
        return workerRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkerResponseDTO> getIdleWorkers() {
        return workerRepository.findByStatus(WorkerStatus.IDLE).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Heartbeat & Status Management
    // -----------------------------------------------------------------------

    @Override
    @Transactional
    public WorkerResponseDTO heartbeat(UUID workerId) {
        Worker worker = findWorkerOrThrow(workerId);
        worker.setLastHeartbeatAt(LocalDateTime.now());

        // Automatically resurrect a worker that recovers from OFFLINE.
        if (worker.getStatus() == WorkerStatus.OFFLINE) {
            log.info("Worker [{}] sent heartbeat after being OFFLINE — restoring to IDLE.", workerId);
            worker.setStatus(WorkerStatus.IDLE);
        }

        return mapToDTO(workerRepository.save(worker));
    }

    @Override
    @Transactional
    public void markBusy(UUID workerId) {
        Worker worker = findWorkerOrThrow(workerId);
        worker.setStatus(WorkerStatus.BUSY);
        workerRepository.save(worker);
        log.debug("Worker [{}] marked BUSY.", workerId);
    }

    @Override
    @Transactional
    public void markIdle(UUID workerId) {
        Worker worker = findWorkerOrThrow(workerId);
        // Do not restore an OFFLINE worker to IDLE; it must send a heartbeat first.
        if (worker.getStatus() != WorkerStatus.OFFLINE) {
            worker.setStatus(WorkerStatus.IDLE);
            workerRepository.save(worker);
            log.debug("Worker [{}] returned to IDLE.", workerId);
        }
    }

    // -----------------------------------------------------------------------
    // Scheduled Heartbeat Health Check
    // -----------------------------------------------------------------------

    /**
     * Runs every 60 seconds. Detects workers that have stopped sending
     * heartbeats and marks them OFFLINE, then re-queues any tasks that were
     * in RUNNING state on those workers.
     */
    @Override
    @Scheduled(fixedDelayString = "${forge.worker.heartbeat.check-interval-ms:60000}")
    @Transactional
    public void checkAndMarkOfflineWorkers() {
        LocalDateTime threshold = LocalDateTime.now()
                .minusSeconds(heartbeatTimeoutSeconds);

        List<Worker> staleWorkers = workerRepository.findAll().stream()
                .filter(w -> w.getStatus() != WorkerStatus.OFFLINE)
                .filter(w -> w.getLastHeartbeatAt() == null
                        || w.getLastHeartbeatAt().isBefore(threshold))
                .toList();

        if (staleWorkers.isEmpty()) {
            log.trace("Heartbeat check: all workers are healthy.");
            return;
        }

        for (Worker stale : staleWorkers) {
            log.warn("Worker [{}] ({}) missed heartbeat deadline — marking OFFLINE.",
                    stale.getId(), stale.getName());
            stale.setStatus(WorkerStatus.OFFLINE);
            workerRepository.save(stale);

            // Re-queue orphaned RUNNING tasks so they are not permanently lost.
            List<Task> orphanedTasks = taskRepository.findByStatus(TaskStatus.RUNNING)
                    .stream()
                    .filter(t -> {
                        // We don't have a direct worker→tasks FK here; re-queue all
                        // RUNNING tasks when a worker goes OFFLINE. In Phase 2 the
                        // C++ engine will carry the task→worker mapping precisely.
                        return true;
                    })
                    .toList();

            // Only re-queue tasks that *this* worker was handling.
            // Since Task doesn't carry workerId directly, we use TaskExecution.
            // For now, conservatively re-queue all RUNNING tasks.
            // TODO (Phase 2): filter by task_executions.worker_id == stale.getId()
            if (!orphanedTasks.isEmpty()) {
                log.warn("Re-queuing {} RUNNING task(s) orphaned by offline worker [{}].",
                        orphanedTasks.size(), stale.getId());
                orphanedTasks.forEach(t -> {
                    t.setStatus(TaskStatus.QUEUED);
                    taskRepository.save(t);
                });
            }
        }
    }

    // -----------------------------------------------------------------------
    // Internal Helpers
    // -----------------------------------------------------------------------

    private Worker findWorkerOrThrow(UUID id) {
        return workerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", id));
    }

    private WorkerResponseDTO mapToDTO(Worker w) {
        return new WorkerResponseDTO(
                w.getId(),
                w.getName(),
                w.getStatus(),
                w.getLastHeartbeatAt(),
                w.getCreatedAt(),
                w.getUpdatedAt()
        );
    }
}
