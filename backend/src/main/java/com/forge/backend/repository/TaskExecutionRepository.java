package com.forge.backend.repository;

import com.forge.backend.model.TaskExecution;
import com.forge.backend.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * JPA repository for the TaskExecution entity.
 */
@Repository
public interface TaskExecutionRepository extends JpaRepository<TaskExecution, UUID> {

    List<TaskExecution> findByTaskId(UUID taskId);

    List<TaskExecution> findByStatus(TaskStatus status);
}
