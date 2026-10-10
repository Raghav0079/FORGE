package com.forge.backend.service;

import com.forge.backend.dto.UserCreateDTO;
import com.forge.backend.dto.UserResponseDTO;

import java.util.UUID;

/**
 * Contract for User business operations.
 * Implementations handle persistence, mapping, and validation rules.
 */
public interface UserService {

    UserResponseDTO createUser(UserCreateDTO createDTO);

    UserResponseDTO getUserById(UUID id);

    UserResponseDTO getUserByUsername(String username);
}
