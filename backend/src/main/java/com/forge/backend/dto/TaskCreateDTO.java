package com.forge.backend.dto;

import com.forge.backend.model.SchedulingAlgorithm;
import jakarta.validation.constraints.*;

/**
 * Payload for submitting a new task to FORGE.
 *
 * @param name               Human-readable task name (required, max 100 chars).
 * @param description        Optional free-text description (max 500 chars).
 * @param payload            Optional JSON/text body the worker will process.
 * @param priority           Dispatch priority 1 (lowest) – 10 (highest). Defaults to 5.
 * @param executionTime      Estimated execution duration in milliseconds (required, >0).
 * @param schedulingAlgorithm Algorithm the scheduler should use for this task.
 */
public record TaskCreateDTO(

        @NotBlank(message = "Task name is required")
        @Size(max = 100, message = "Task name must not exceed 100 characters")
        String name,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        String payload,

        @NotNull(message = "Priority is required")
        @Min(value = 1, message = "Priority must be at least 1")
        @Max(value = 10, message = "Priority must not exceed 10")
        Integer priority,

        @NotNull(message = "Execution time is required")
        @Positive(message = "Execution time must be a positive number of milliseconds")
        Long executionTime,

        @NotNull(message = "Scheduling algorithm must be specified")
        SchedulingAlgorithm schedulingAlgorithm
) {}
