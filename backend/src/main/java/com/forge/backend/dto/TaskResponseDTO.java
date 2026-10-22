package com.forge.backend.dto;

import com.forge.backend.model.SchedulingAlgorithm;
import com.forge.backend.model.TaskStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read-only projection of a Task entity returned by the API.
 *
 * @param id                  Unique task identifier.
 * @param name                Task name.
 * @param description         Optional description.
 * @param priority            Dispatch priority (1-10).
 * @param executionTime       Estimated execution time in milliseconds.
 * @param status              Current lifecycle status.
 * @param schedulingAlgorithm Algorithm assigned to this task.
 * @param arrivalTime         When the task entered the system.
 * @param createdAt           Persistence timestamp.
 * @param updatedAt           Last-modified timestamp.
 */
public record TaskResponseDTO(
        UUID id,
        String name,
        String description,
        Integer priority,
        Long executionTime,
        TaskStatus status,
        SchedulingAlgorithm schedulingAlgorithm,
        LocalDateTime arrivalTime,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
