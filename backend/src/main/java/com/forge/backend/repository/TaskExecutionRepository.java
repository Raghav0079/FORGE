package com.forge.backend.repository;

import com.forge.backend.model.TaskExecution;
import com.forge.backend.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA repository for the TaskExecution entity.
 */
@Repository
public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {

    List<TaskExecution> findByTaskId(Long taskId);

    List<TaskExecution> findByStatus(TaskStatus status);
}
