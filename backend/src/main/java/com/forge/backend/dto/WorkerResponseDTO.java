package com.forge.backend.dto;

import com.forge.backend.model.WorkerStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read-only projection of a Worker entity returned by the API.
 *
 * @param id                Unique worker identifier.
 * @param name              Worker display name.
 * @param status            Current status (IDLE / BUSY / OFFLINE).
 * @param lastHeartbeatAt   Timestamp of the most recent heartbeat.
 * @param createdAt         Registration timestamp.
 * @param updatedAt         Last-modified timestamp.
 */
public record WorkerResponseDTO(
        UUID id,
        String name,
        WorkerStatus status,
        LocalDateTime lastHeartbeatAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
