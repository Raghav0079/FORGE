package com.forge.backend.controller;

import com.forge.backend.dto.TaskCreateDTO;
import com.forge.backend.dto.TaskResponseDTO;
import com.forge.backend.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor

public class TaskController{

    private final TaskService taskService;

    @PostMapping
    public ResponseEntity<TaskResponseDTO> submitTask(@Valid @RequestBody TaskCreateDTO createDTO) {
        TaskResponseDTO response = taskService.submitTask(createDTO);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponseDTO> getTaskById(@PathVariable UUID id) {
        return ResponseEntity.ok(taskService.getTaskById(id));
    }

    @GetMapping
    public ResponseEntity<List<TaskResponseDTO>> getAllTasks() {
        return ResponseEntity.ok(taskService.getAllTasks());
    }
}