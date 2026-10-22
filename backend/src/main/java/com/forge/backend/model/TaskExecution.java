package com.forge.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable audit record for a single task execution attempt.
 * <p>
 * Scheduling metrics stored here mirror classical OS scheduling definitions:
 * <ul>
 *   <li>{@code waitingTimeMs}    — time the task spent in the QUEUED state
 *                                   before a worker picked it up.</li>
 *   <li>{@code turnaroundTimeMs} — total time from task arrival/submission
 *                                   until execution completed (wait + run).</li>
 *   <li>{@code durationMs}       — pure CPU/execution time (run phase only).</li>
 * </ul>
 */
@Entity
@Table(name = "task_executions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** The task this execution record belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    /** Identifier of the worker node that executed this task. */
    @Column(name = "worker_id")
    private UUID workerId;

    /** Wall-clock time when the worker began processing. */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** Wall-clock time when execution finished (success or failure). */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /** Pure execution duration in milliseconds (finishedAt − startedAt). */
    @Column(name = "duration_ms")
    private Long durationMs;

    /**
     * Time the task spent waiting in the queue (QUEUED → RUNNING transition).
     * Calculated as: {@code startedAt − task.arrivalTime}.
     */
    @Column(name = "waiting_time_ms")
    private Long waitingTimeMs;

    /**
     * End-to-end elapsed time from arrival until completion.
     * Calculated as: {@code waitingTimeMs + durationMs}.
     */
    @Column(name = "turnaround_time_ms")
    private Long turnaroundTimeMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    /** Optional error detail captured on FAILED executions. */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
