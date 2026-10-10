package com.forge.backend.repository;

import com.forge.backend.model.Task;
import com.forge.backend.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * JPA repository for the Task entity.
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByStatus(TaskStatus status);

    List<Task> findByOrderByPriorityDesc();
}
