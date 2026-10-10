package com.forge.backend.service.impl;

import com.forge.backend.dto.TaskCreateDTO;
import com.forge.backend.dto.TaskResponseDTO;
import com.forge.backend.model.Task;
import com.forge.backend.model.TaskStatus;
import com.forge.backend.repository.TaskRepository;
import com.forge.backend.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;

    @Override
    @Transactional
    public TaskResponseDTO submitTask(TaskCreateDTO createDTO) {
        Task task = new Task();
        task.setName(createDTO.name());
        task.setPayload(createDTO.payload());
        task.setAlgorithmType(createDTO.algorithm());
        task.setStatus(TaskStatus.CREATED); // Initial state before entering the queue

        return mapToResponseDTO(taskRepository.save(task));
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponseDTO getTaskById(UUID id) {
        return taskRepository.findById(id)
                .map(this::mapToResponseDTO)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponseDTO> getAllTasks() {
        return taskRepository.findAll().stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    private TaskResponseDTO mapToResponseDTO(Task task) {
        return new TaskResponseDTO(
                task.getId(),
                task.getName(),
                task.getStatus(),
                task.getAlgorithmType(),
                task.getCreatedAt()
        );
    }
}