package com.forge.backend.controller;

import com.forge.backend.dto.TaskCreateDTO;
import com.forge.backend.dto.TaskExecutionResponseDTO;
import com.forge.backend.dto.TaskResponseDTO;
import com.forge.backend.model.SchedulingAlgorithm;
import com.forge.backend.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller exposing the full Task lifecycle API.
 * <p>
 * Base path: {@code /api/tasks}
 *
 * <pre>
 * POST   /api/tasks                              → submit new task
 * GET    /api/tasks                              → list all tasks
 * GET    /api/tasks?status={STATUS}              → filter by status
 * GET    /api/tasks/{id}                         → get single task
 * POST   /api/tasks/{id}/queue                   → CREATED → QUEUED
 * POST   /api/tasks/{id}/start?workerId={uuid}   → QUEUED  → RUNNING
 * POST   /api/tasks/{id}/complete                → RUNNING → COMPLETED
 * POST   /api/tasks/{id}/fail                    → RUNNING → FAILED
 * POST   /api/tasks/{id}/cancel                  → * → CANCELLED
 * GET    /api/tasks/{id}/executions              → execution audit history
 * GET    /api/tasks/dispatch?algorithm=X&workerId=Y → select next task
 * </pre>
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    // -----------------------------------------------------------------------
    // Submission & Read
    // -----------------------------------------------------------------------

    @PostMapping
    public ResponseEntity<TaskResponseDTO> submitTask(
            @Valid @RequestBody TaskCreateDTO createDTO) {
        return new ResponseEntity<>(taskService.submitTask(createDTO), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<TaskResponseDTO>> getAllTasks(
            @RequestParam(required = false) String status) {

        List<TaskResponseDTO> tasks = (status != null && !status.isBlank())
                ? taskService.getTasksByStatus(status)
                : taskService.getAllTasks();
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponseDTO> getTaskById(@PathVariable UUID id) {
        return ResponseEntity.ok(taskService.getTaskById(id));
    }

    // -----------------------------------------------------------------------
    // Lifecycle Transitions
    // -----------------------------------------------------------------------

    /** Advance a CREATED task into the QUEUED state. */
    @PostMapping("/{id}/queue")
    public ResponseEntity<TaskResponseDTO> queueTask(@PathVariable UUID id) {
        return ResponseEntity.ok(taskService.queueTask(id));
    }

    /**
     * Dispatch a QUEUED task to a specific worker (QUEUED → RUNNING).
     * The workerId is supplied by the caller (typically the scheduler loop
     * or a worker polling for work).
     */
    @PostMapping("/{id}/start")
    public ResponseEntity<TaskResponseDTO> startTask(
            @PathVariable UUID id,
            @RequestParam UUID workerId) {
        return ResponseEntity.ok(taskService.startTask(id, workerId));
    }

    /** Mark a RUNNING task as COMPLETED. */
    @PostMapping("/{id}/complete")
    public ResponseEntity<TaskResponseDTO> completeTask(@PathVariable UUID id) {
        return ResponseEntity.ok(taskService.completeTask(id));
    }

    /**
     * Mark a RUNNING task as FAILED with an optional error message.
     * The body is a JSON object: {@code {"errorMessage": "..."}}
     */
    @PostMapping("/{id}/fail")
    public ResponseEntity<TaskResponseDTO> failTask(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String errorMessage = (body != null) ? body.getOrDefault("errorMessage", "") : "";
        return ResponseEntity.ok(taskService.failTask(id, errorMessage));
    }

    /** Cancel a task in CREATED or QUEUED state. */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<TaskResponseDTO> cancelTask(@PathVariable UUID id) {
        return ResponseEntity.ok(taskService.cancelTask(id));
    }

    // -----------------------------------------------------------------------
    // Execution History
    // -----------------------------------------------------------------------

    /** Retrieve all TaskExecution audit records for a given task. */
    @GetMapping("/{id}/executions")
    public ResponseEntity<List<TaskExecutionResponseDTO>> getExecutionHistory(
            @PathVariable UUID id) {
        return ResponseEntity.ok(taskService.getExecutionHistory(id));
    }

    // -----------------------------------------------------------------------
    // Scheduling Dispatch
    // -----------------------------------------------------------------------

    /**
     * Ask the scheduler to select the next QUEUED task for a given worker
     * using the specified algorithm, without starting it. Returns 204 if the
     * queue is empty.
     *
     * @param algorithm The scheduling algorithm to apply (e.g. FCFS, SJF).
     * @param workerId  UUID of the requesting worker.
     */
    @GetMapping("/dispatch")
    public ResponseEntity<TaskResponseDTO> dispatchNextTask(
            @RequestParam SchedulingAlgorithm algorithm,
            @RequestParam UUID workerId) {

        Optional<TaskResponseDTO> selected =
                taskService.selectNextTask(algorithm, workerId);
        return selected
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}