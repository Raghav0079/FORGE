-- ============================================================
-- V3__add_heartbeat_and_metrics.sql
-- Extends workers with a heartbeat timestamp so the health
-- monitor can detect stale nodes, and adds OS-scheduling
-- metrics (waiting_time_ms, turnaround_time_ms) to
-- task_executions for scientific evaluation in Phase 5.
-- ============================================================

-- workers: track when the node last reported liveness
ALTER TABLE workers
    ADD COLUMN IF NOT EXISTS last_heartbeat_at TIMESTAMP;

-- Seed existing rows so they are not immediately considered offline
UPDATE workers
SET last_heartbeat_at = now()
WHERE last_heartbeat_at IS NULL;

-- task_executions: OS scheduling algorithm metrics
ALTER TABLE task_executions
    ADD COLUMN IF NOT EXISTS waiting_time_ms    BIGINT;

ALTER TABLE task_executions
    ADD COLUMN IF NOT EXISTS turnaround_time_ms BIGINT;

-- Index: scheduler hot-path — pick oldest QUEUED task (FCFS)
CREATE INDEX IF NOT EXISTS idx_tasks_status_arrival
    ON tasks (status, arrival_time ASC);

-- Index: scheduler hot-path — pick shortest QUEUED task (SJF)
CREATE INDEX IF NOT EXISTS idx_tasks_status_execution_time
    ON tasks (status, execution_time ASC);

-- Index: scheduler hot-path — pick highest-priority QUEUED task
CREATE INDEX IF NOT EXISTS idx_tasks_status_priority
    ON tasks (status, priority DESC);
