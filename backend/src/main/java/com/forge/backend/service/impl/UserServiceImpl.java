package com.forge.backend.service.impl;

import com.forge.backend.dto.UserCreateDTO;
import com.forge.backend.dto.UserResponseDTO;
import com.forge.backend.model.User;
import com.forge.backend.repository.UserRepository;
import com.forge.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Concrete implementation of UserService.
 *
 * NOTE: Password hashing is intentionally left as a plain assignment here
 * because the Security subsystem (Scala module) is not yet integrated.
 * Replace this with a proper PasswordEncoder once the security layer is wired.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public UserResponseDTO createUser(UserCreateDTO createDTO) {
        if (userRepository.existsByUsername(createDTO.username())) {
            throw new IllegalArgumentException("Username already taken: " + createDTO.username());
        }
        if (userRepository.existsByEmail(createDTO.email())) {
            throw new IllegalArgumentException("Email already registered: " + createDTO.email());
        }

        User user = new User();
        user.setUsername(createDTO.username());
        user.setEmail(createDTO.email());
        // TODO: replace with PasswordEncoder.encode() once security module is integrated
        user.setPasswordHash(createDTO.password());
        user.setRole(createDTO.role() != null ? createDTO.role() : "USER");

        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    @Override
    public UserResponseDTO getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
        return toResponse(user);
    }

    @Override
    public UserResponseDTO getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found with username: " + username));
        return toResponse(user);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private UserResponseDTO toResponse(User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
