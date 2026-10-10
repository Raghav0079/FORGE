package com.forge.backend.dto;

import com.forge.backend.model.SchedulingAlgorithm;
import com.forge.backend.model.TaskStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record TaskResponseDTO(
        UUID id,
        String name,
        TaskStatus status,
        SchedulingAlgorithm schedulingAlgorithm,
        LocalDateTime createdAt
) {}

