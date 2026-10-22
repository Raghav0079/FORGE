package com.forge.backend.service.impl;

import com.forge.backend.dto.TaskCreateDTO;
import com.forge.backend.dto.TaskExecutionResponseDTO;
import com.forge.backend.dto.TaskResponseDTO;
import com.forge.backend.exception.InvalidStateTransitionException;
import com.forge.backend.exception.ResourceNotFoundException;
import com.forge.backend.model.*;
import com.forge.backend.repository.TaskExecutionRepository;
import com.forge.backend.repository.TaskRepository;
import com.forge.backend.repository.WorkerRepository;
import com.forge.backend.scheduler.SchedulerManager;
import com.forge.backend.service.TaskService;
import com.forge.backend.service.WorkerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Production implementation of {@link TaskService}.
 * <p>
 * Enforces the task state machine:
 * <pre>
 *   CREATED ──► QUEUED ──► RUNNING ──► COMPLETED
 *                  │                 └──► FAILED
 *                  └────────────────────► CANCELLED
 * </pre>
 * Every terminal transition closes the open {@link TaskExecution} audit record
 * and computes OS scheduling metrics:
 * <ul>
 *   <li><b>waitingTimeMs</b>    = startedAt − task.arrivalTime (in ms)</li>
 *   <li><b>durationMs</b>       = finishedAt − startedAt (in ms)</li>
 *   <li><b>turnaroundTimeMs</b> = waitingTimeMs + durationMs</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    /** Statuses from which no further transitions are allowed. */
    private static final Set<TaskStatus> TERMINAL_STATUSES =
            Set.of(TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.CANCELLED);

    private final TaskRepository taskRepository;
    private final TaskExecutionRepository taskExecutionRepository;
    private final WorkerRepository workerRepository;
    private final SchedulerManager schedulerManager;
    private final WorkerService workerService;

    // -----------------------------------------------------------------------
    // Submission
    // -----------------------------------------------------------------------

    @Override
    @Transactional
    public TaskResponseDTO submitTask(TaskCreateDTO createDTO) {
        Task task = Task.builder()
                .name(createDTO.name())
                .description(createDTO.description())
                .payload(createDTO.payload())
                .priority(createDTO.priority())
                .executionTime(createDTO.executionTime())
                .schedulingAlgorithm(createDTO.schedulingAlgorithm())
                .status(TaskStatus.CREATED)
                .build();
        // arrivalTime and timestamps are set by @PrePersist
        Task saved = taskRepository.save(task);
        log.info("Task [{}] '{}' submitted with status CREATED.", saved.getId(), saved.getName());
        return mapToDTO(saved);
    }

    // -----------------------------------------------------------------------
    // Read Operations
    // -----------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public TaskResponseDTO getTaskById(UUID id) {
        return taskRepository.findById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponseDTO> getAllTasks() {
        return taskRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponseDTO> getTasksByStatus(String status) {
        TaskStatus taskStatus;
        try {
            taskStatus = TaskStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown task status: " + status);
        }
        return taskRepository.findByStatus(taskStatus).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Lifecycle Transitions
    // -----------------------------------------------------------------------

    @Override
    @Transactional
    public TaskResponseDTO queueTask(UUID taskId) {
        Task task = findTaskOrThrow(taskId);
        assertTransitionAllowed(task, TaskStatus.CREATED, TaskStatus.QUEUED);
        task.setStatus(TaskStatus.QUEUED);
        Task saved = taskRepository.save(task);
        log.info("Task [{}] advanced to QUEUED.", taskId);
        return mapToDTO(saved);
    }

    @Override
    @Transactional
    public TaskResponseDTO startTask(UUID taskId, UUID workerId) {
        Task task = findTaskOrThrow(taskId);
        assertTransitionAllowed(task, TaskStatus.QUEUED, TaskStatus.RUNNING);

        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", workerId));

        // Create audit record
        LocalDateTime now = LocalDateTime.now();
        long waitingTimeMs = ChronoUnit.MILLIS.between(task.getArrivalTime(), now);

        TaskExecution execution = TaskExecution.builder()
                .task(task)
                .workerId(workerId)
                .startedAt(now)
                .status(TaskStatus.RUNNING)
                .waitingTimeMs(waitingTimeMs)
                .build();
        taskExecutionRepository.save(execution);

        // Mark the worker as busy
        workerService.markBusy(workerId);

        task.setStatus(TaskStatus.RUNNING);
        Task saved = taskRepository.save(task);
        log.info("Task [{}] RUNNING on worker [{}]. Waiting time: {}ms.", taskId, workerId, waitingTimeMs);
        return mapToDTO(saved);
    }

    @Override
    @Transactional
    public TaskResponseDTO completeTask(UUID taskId) {
        Task task = findTaskOrThrow(taskId);
        assertTransitionAllowed(task, TaskStatus.RUNNING, TaskStatus.COMPLETED);

        closeExecution(task, TaskStatus.COMPLETED, null);

        task.setStatus(TaskStatus.COMPLETED);
        Task saved = taskRepository.save(task);
        log.info("Task [{}] COMPLETED.", taskId);
        return mapToDTO(saved);
    }

    @Override
    @Transactional
    public TaskResponseDTO failTask(UUID taskId, String errorMessage) {
        Task task = findTaskOrThrow(taskId);
        assertTransitionAllowed(task, TaskStatus.RUNNING, TaskStatus.FAILED);

        closeExecution(task, TaskStatus.FAILED, errorMessage);

        task.setStatus(TaskStatus.FAILED);
        Task saved = taskRepository.save(task);
        log.warn("Task [{}] FAILED. Reason: {}", taskId, errorMessage);
        return mapToDTO(saved);
    }

    @Override
    @Transactional
    public TaskResponseDTO cancelTask(UUID taskId) {
        Task task = findTaskOrThrow(taskId);
        if (TERMINAL_STATUSES.contains(task.getStatus())) {
            throw new InvalidStateTransitionException(taskId, task.getStatus(), TaskStatus.CANCELLED);
        }
        if (task.getStatus() == TaskStatus.RUNNING) {
            throw new InvalidStateTransitionException(
                    "Cannot cancel a RUNNING task at the Java layer. " +
                    "Send a cancel signal to the C++ engine in Phase 2.");
        }
        task.setStatus(TaskStatus.CANCELLED);
        Task saved = taskRepository.save(task);
        log.info("Task [{}] CANCELLED from status {}.", taskId, task.getStatus());
        return mapToDTO(saved);
    }

    // -----------------------------------------------------------------------
    // Scheduling Dispatch
    // -----------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<TaskResponseDTO> selectNextTask(SchedulingAlgorithm algorithm, UUID workerId) {
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", workerId));

        List<Task> queue = taskRepository.findByStatus(TaskStatus.QUEUED);
        return schedulerManager.selectNext(algorithm, queue, worker)
                .map(this::mapToDTO);
    }

    // -----------------------------------------------------------------------
    // Audit History
    // -----------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<TaskExecutionResponseDTO> getExecutionHistory(UUID taskId) {
        // Ensure task exists first
        if (!taskRepository.existsById(taskId)) {
            throw new ResourceNotFoundException("Task", taskId);
        }
        return taskExecutionRepository.findByTaskId(taskId).stream()
                .map(this::mapExecutionToDTO)
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Private Helpers
    // -----------------------------------------------------------------------

    /**
     * Finds and closes the open RUNNING execution record for a task.
     * Computes durationMs and turnaroundTimeMs and updates worker status.
     */
    private void closeExecution(Task task, TaskStatus outcomeStatus, String errorMessage) {
        List<TaskExecution> executions = taskExecutionRepository.findByTaskId(task.getId());

        // Find the most recent RUNNING execution record
        executions.stream()
                .filter(e -> e.getStatus() == TaskStatus.RUNNING)
                .max(java.util.Comparator.comparing(TaskExecution::getStartedAt))
                .ifPresentOrElse(exec -> {
                    LocalDateTime finished = LocalDateTime.now();
                    long durationMs = ChronoUnit.MILLIS.between(exec.getStartedAt(), finished);
                    long waitingMs = exec.getWaitingTimeMs() != null ? exec.getWaitingTimeMs() : 0L;

                    exec.setFinishedAt(finished);
                    exec.setDurationMs(durationMs);
                    exec.setTurnaroundTimeMs(waitingMs + durationMs);
                    exec.setStatus(outcomeStatus);
                    exec.setErrorMessage(errorMessage);
                    taskExecutionRepository.save(exec);

                    // Return worker to idle pool
                    if (exec.getWorkerId() != null) {
                        workerService.markIdle(exec.getWorkerId());
                    }

                    log.debug("Execution [{}] closed: duration={}ms, turnaround={}ms.",
                            exec.getId(), durationMs, waitingMs + durationMs);
                }, () -> log.warn(
                        "No open RUNNING execution found for task [{}] during {} transition.",
                        task.getId(), outcomeStatus));
    }

    /** Guards against illegal state machine transitions. */
    private void assertTransitionAllowed(Task task, TaskStatus expectedCurrent, TaskStatus next) {
        if (task.getStatus() != expectedCurrent) {
            throw new InvalidStateTransitionException(task.getId(), task.getStatus(), next);
        }
        if (TERMINAL_STATUSES.contains(task.getStatus())) {
            throw new InvalidStateTransitionException(task.getId(), task.getStatus(), next);
        }
    }

    private Task findTaskOrThrow(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    private TaskResponseDTO mapToDTO(Task task) {
        return new TaskResponseDTO(
                task.getId(),
                task.getName(),
                task.getDescription(),
                task.getPriority(),
                task.getExecutionTime(),
                task.getStatus(),
                task.getSchedulingAlgorithm(),
                task.getArrivalTime(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private TaskExecutionResponseDTO mapExecutionToDTO(TaskExecution e) {
        return new TaskExecutionResponseDTO(
                e.getId(),
                e.getTask().getId(),
                e.getWorkerId(),
                e.getStatus(),
                e.getStartedAt(),
                e.getFinishedAt(),
                e.getDurationMs(),
                e.getWaitingTimeMs(),
                e.getTurnaroundTimeMs(),
                e.getErrorMessage(),
                e.getCreatedAt()
        );
    }
}