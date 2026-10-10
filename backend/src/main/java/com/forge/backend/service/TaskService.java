package com.forge.backend.service;

import com.forge.backend.dto.TaskCreateDTO;
import com.forge.backend.dto.TaskResponseDTO;
import java.util.List;
import java.util.UUID;

public interface TaskService {
    TaskResponseDTO submitTask(TaskCreateDTO createDTO);
    TaskResponseDTO getTaskById(UUID id);
    List<TaskResponseDTO> getAllTasks();
}