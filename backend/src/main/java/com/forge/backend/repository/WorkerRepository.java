package com.forge.backend.repository;

import com.forge.backend.model.Worker;
import com.forge.backend.model.WorkerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * JPA repository for the Worker entity.
 */
@Repository
public interface WorkerRepository extends JpaRepository<Worker, UUID> {

    List<Worker> findByStatus(WorkerStatus status);
}
