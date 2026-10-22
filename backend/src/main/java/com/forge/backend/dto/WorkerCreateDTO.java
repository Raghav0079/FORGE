package com.forge.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for registering a new worker node with FORGE.
 *
 * @param name Human-readable worker identifier (e.g. "worker-node-01").
 */
public record WorkerCreateDTO(

        @NotBlank(message = "Worker name is required")
        @Size(max = 100, message = "Worker name must not exceed 100 characters")
        String name
) {}
