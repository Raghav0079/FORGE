package com.forge.backend.dto;

import com.forge.backend.model.AlgorithmType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TaskCreateDTO(
        @NotBlank(message = "Task name is required") String name,
        String payload, // The actual work/command the worker will execute
        @NotNull(message = "Scheduling algorithm must be specified") AlgorithmType algorithm
){}

