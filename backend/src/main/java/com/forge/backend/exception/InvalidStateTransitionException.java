package com.forge.backend.exception;

import com.forge.backend.model.TaskStatus;

import java.util.UUID;

/**
 * Thrown when a task lifecycle transition is illegal given its current state.
 * For example, attempting to cancel a COMPLETED task.
 * Maps to HTTP 409 Conflict via {@link GlobalExceptionHandler}.
 */
public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(UUID taskId, TaskStatus current, TaskStatus attempted) {
        super(String.format(
                "Cannot transition task [%s] from %s to %s",
                taskId, current, attempted
        ));
    }

    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
