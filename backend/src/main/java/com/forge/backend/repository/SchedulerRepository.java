package com.forge.backend.repository;

import com.forge.backend.model.Scheduler;
import com.forge.backend.model.SchedulerStatus;
import com.forge.backend.model.SchedulingAlgorithm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * JPA repository for the Scheduler entity.
 */
@Repository
public interface SchedulerRepository extends JpaRepository<Scheduler, UUID> {

    List<Scheduler> findByStatus(SchedulerStatus status);

    List<Scheduler> findByAlgorithm(SchedulingAlgorithm algorithm);
}
