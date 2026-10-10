package com.forge.backend.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outbound DTO returned to API consumers after a User operation.
 * Never exposes passwordHash or other sensitive internal fields.
 */
public record UserResponseDTO(
        UUID id,
        String username,
        String email,
        String role,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
