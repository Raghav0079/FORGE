package com.forge.backend.dto;

import com.forge.backend.model.TaskStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read-only projection of a TaskExecution audit record.
 *
 * @param id                Execution record id.
 * @param taskId            Parent task id.
 * @param workerId          Worker that processed this execution.
 * @param status            Outcome status (RUNNING / COMPLETED / FAILED).
 * @param startedAt         When the worker began processing.
 * @param finishedAt        When execution finished.
 * @param durationMs        Pure CPU time (finishedAt − startedAt).
 * @param waitingTimeMs     Queue wait time (startedAt − task.arrivalTime).
 * @param turnaroundTimeMs  End-to-end time (waitingTimeMs + durationMs).
 * @param errorMessage      Error detail if status is FAILED.
 * @param createdAt         Record creation timestamp.
 */
public record TaskExecutionResponseDTO(
        UUID id,
        UUID taskId,
        UUID workerId,
        TaskStatus status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long durationMs,
        Long waitingTimeMs,
        Long turnaroundTimeMs,
        String errorMessage,
        LocalDateTime createdAt
) {}
