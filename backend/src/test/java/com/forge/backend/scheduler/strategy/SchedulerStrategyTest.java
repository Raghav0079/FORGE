package com.forge.backend.scheduler.strategy;

import com.forge.backend.model.SchedulingAlgorithm;
import com.forge.backend.model.Task;
import com.forge.backend.model.TaskStatus;
import com.forge.backend.model.Worker;
import com.forge.backend.model.WorkerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for all four scheduling strategy implementations.
 * Zero Spring context — pure JUnit 5 + AssertJ.
 */
class SchedulerStrategyTest {

    private Worker worker;
    private LocalDateTime base;

    @BeforeEach
    void setUp() {
        worker = Worker.builder()
                .id(UUID.randomUUID())
                .name("test-worker")
                .status(WorkerStatus.IDLE)
                .build();
        base = LocalDateTime.of(2024, 1, 1, 10, 0, 0);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Task task(String name, int priority, long execMs, LocalDateTime arrival) {
        return Task.builder()
                .id(UUID.randomUUID())
                .name(name)
                .priority(priority)
                .executionTime(execMs)
                .status(TaskStatus.QUEUED)
                .schedulingAlgorithm(SchedulingAlgorithm.FCFS)
                .arrivalTime(arrival)
                .createdAt(arrival)
                .updatedAt(arrival)
                .build();
    }

    // -----------------------------------------------------------------------
    // FCFS
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("FCFSScheduler")
    class FCFSTests {

        private final FCFSScheduler strategy = new FCFSScheduler();

        @Test
        @DisplayName("returns empty for empty queue")
        void emptyQueue() {
            assertThat(strategy.selectNextTask(Collections.emptyList(), worker)).isEmpty();
        }

        @Test
        @DisplayName("returns the only task in a singleton queue")
        void singleTask() {
            Task t = task("T1", 5, 100, base);
            Optional<Task> result = strategy.selectNextTask(List.of(t), worker);
            assertThat(result).isPresent().contains(t);
        }

        @Test
        @DisplayName("selects task with earliest arrivalTime")
        void selectsEarliest() {
            Task t1 = task("T-early", 5, 100, base);
            Task t2 = task("T-late",  5, 100, base.plusMinutes(5));
            Task t3 = task("T-mid",   5, 100, base.plusMinutes(2));

            Optional<Task> result = strategy.selectNextTask(List.of(t2, t3, t1), worker);
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("T-early");
        }

        @Test
        @DisplayName("reports correct algorithmType")
        void algorithmType() {
            assertThat(strategy.algorithmType()).isEqualTo(SchedulingAlgorithm.FCFS);
        }
    }

    // -----------------------------------------------------------------------
    // SJF
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("SJFScheduler")
    class SJFTests {

        private final SJFScheduler strategy = new SJFScheduler();

        @Test
        @DisplayName("returns empty for empty queue")
        void emptyQueue() {
            assertThat(strategy.selectNextTask(Collections.emptyList(), worker)).isEmpty();
        }

        @Test
        @DisplayName("selects task with smallest executionTime")
        void selectsShortest() {
            Task t1 = task("Long",   5, 5000, base);
            Task t2 = task("Short",  5,  100, base.plusSeconds(1));
            Task t3 = task("Medium", 5, 1000, base.plusSeconds(2));

            Optional<Task> result = strategy.selectNextTask(List.of(t1, t3, t2), worker);
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("Short");
        }

        @Test
        @DisplayName("breaks ties by earlier arrivalTime")
        void tieBreakByArrival() {
            Task t1 = task("A", 5, 500, base.plusSeconds(10));
            Task t2 = task("B", 5, 500, base);              // same exec, earlier arrival

            Optional<Task> result = strategy.selectNextTask(List.of(t1, t2), worker);
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("B");
        }

        @Test
        @DisplayName("reports correct algorithmType")
        void algorithmType() {
            assertThat(strategy.algorithmType()).isEqualTo(SchedulingAlgorithm.SJF);
        }
    }

    // -----------------------------------------------------------------------
    // Priority
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("PriorityScheduler")
    class PriorityTests {

        private final PriorityScheduler strategy = new PriorityScheduler();

        @Test
        @DisplayName("returns empty for empty queue")
        void emptyQueue() {
            assertThat(strategy.selectNextTask(Collections.emptyList(), worker)).isEmpty();
        }

        @Test
        @DisplayName("selects task with highest priority")
        void selectsHighestPriority() {
            Task t1 = task("Low",  1, 100, base);
            Task t2 = task("High", 9, 100, base.plusSeconds(1));
            Task t3 = task("Mid",  5, 100, base.plusSeconds(2));

            Optional<Task> result = strategy.selectNextTask(List.of(t1, t3, t2), worker);
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("High");
        }

        @Test
        @DisplayName("breaks priority ties by earlier arrivalTime")
        void tieBreakByArrival() {
            Task t1 = task("Earlier", 7, 100, base);
            Task t2 = task("Later",   7, 100, base.plusSeconds(30)); // same prio, later arrival

            Optional<Task> result = strategy.selectNextTask(List.of(t2, t1), worker);
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("Earlier");
        }

        @Test
        @DisplayName("reports correct algorithmType")
        void algorithmType() {
            assertThat(strategy.algorithmType()).isEqualTo(SchedulingAlgorithm.PRIORITY);
        }
    }

    // -----------------------------------------------------------------------
    // Round Robin
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("RoundRobinScheduler")
    class RoundRobinTests {

        // Fresh instance per test so the internal pointer resets.
        private RoundRobinScheduler strategy;

        @BeforeEach
        void newStrategy() {
            strategy = new RoundRobinScheduler();
        }

        @Test
        @DisplayName("returns empty for empty queue")
        void emptyQueue() {
            assertThat(strategy.selectNextTask(Collections.emptyList(), worker)).isEmpty();
        }

        @Test
        @DisplayName("cycles through tasks in arrival order")
        void cyclesInOrder() {
            Task t1 = task("T1", 5, 100, base);
            Task t2 = task("T2", 5, 100, base.plusSeconds(1));
            Task t3 = task("T3", 5, 100, base.plusSeconds(2));
            List<Task> queue = List.of(t3, t1, t2); // intentionally unsorted

            // First call should return the task with the earliest arrival (T1)
            Optional<Task> first = strategy.selectNextTask(queue, worker);
            assertThat(first).isPresent();
            assertThat(first.get().getName()).isEqualTo("T1");

            // Second call advances pointer → T2
            Optional<Task> second = strategy.selectNextTask(queue, worker);
            assertThat(second).isPresent();
            assertThat(second.get().getName()).isEqualTo("T2");

            // Third call → T3
            Optional<Task> third = strategy.selectNextTask(queue, worker);
            assertThat(third).isPresent();
            assertThat(third.get().getName()).isEqualTo("T3");

            // Fourth call wraps around → T1 again
            Optional<Task> fourth = strategy.selectNextTask(queue, worker);
            assertThat(fourth).isPresent();
            assertThat(fourth.get().getName()).isEqualTo("T1");
        }

        @Test
        @DisplayName("reports correct algorithmType")
        void algorithmType() {
            assertThat(strategy.algorithmType()).isEqualTo(SchedulingAlgorithm.ROUND_ROBIN);
        }
    }
}
