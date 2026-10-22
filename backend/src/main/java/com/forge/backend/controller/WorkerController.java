package com.forge.backend.controller;

import com.forge.backend.dto.WorkerCreateDTO;
import com.forge.backend.dto.WorkerResponseDTO;
import com.forge.backend.service.WorkerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller exposing worker node management and heartbeat API.
 * <p>
 * Base path: {@code /api/workers}
 *
 * <pre>
 * POST   /api/workers                        → register new worker
 * GET    /api/workers                        → list all workers
 * GET    /api/workers?status=IDLE            → filter by status
 * GET    /api/workers/{id}                   → get single worker
 * POST   /api/workers/{id}/heartbeat         → record liveness heartbeat
 * </pre>
 */
@RestController
@RequestMapping("/api/workers")
@RequiredArgsConstructor
public class WorkerController {

    private final WorkerService workerService;

    // -----------------------------------------------------------------------
    // Registration & Read
    // -----------------------------------------------------------------------

    @PostMapping
    public ResponseEntity<WorkerResponseDTO> registerWorker(
            @Valid @RequestBody WorkerCreateDTO createDTO) {
        return new ResponseEntity<>(workerService.registerWorker(createDTO), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<WorkerResponseDTO>> getAllWorkers(
            @RequestParam(required = false) String status) {

        // Simple status filter at the controller level for now.
        // Phase 5 will push this into a proper JPA Specification.
        List<WorkerResponseDTO> workers;
        if ("IDLE".equalsIgnoreCase(status)) {
            workers = workerService.getIdleWorkers();
        } else {
            workers = workerService.getAllWorkers();
        }
        return ResponseEntity.ok(workers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkerResponseDTO> getWorkerById(@PathVariable UUID id) {
        return ResponseEntity.ok(workerService.getWorkerById(id));
    }

    // -----------------------------------------------------------------------
    // Heartbeat
    // -----------------------------------------------------------------------

    /**
     * Workers call this endpoint periodically to signal liveness.
     * If a worker was OFFLINE it is automatically restored to IDLE.
     */
    @PostMapping("/{id}/heartbeat")
    public ResponseEntity<WorkerResponseDTO> heartbeat(@PathVariable UUID id) {
        return ResponseEntity.ok(workerService.heartbeat(id));
    }
}
