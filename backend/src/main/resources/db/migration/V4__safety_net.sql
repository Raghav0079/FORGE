-- ============================================================
-- V4__safety_net.sql
-- Idempotent catch-all migration.
-- Re-applies every structural change from V2 and V3 using
-- IF NOT EXISTS guards so that it is safe to run regardless
-- of whether V2/V3 succeeded, partially ran, or never ran.
-- This migration is a no-op on a database that is already
-- fully up-to-date.
-- ============================================================

-- ============================================================
-- SECTION 1: Core columns that V2 should have added to tasks
-- ============================================================

-- payload column (V2, Step 2)
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS payload TEXT;

-- ============================================================
-- SECTION 2: workers table  (V2, Step 7)
-- ============================================================

CREATE TABLE IF NOT EXISTS workers (
    id                UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name              VARCHAR(100) NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'IDLE',
    last_heartbeat_at TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now()
);

-- last_heartbeat_at added by V3 — add if somehow still missing
ALTER TABLE workers ADD COLUMN IF NOT EXISTS last_heartbeat_at TIMESTAMP;

-- ============================================================
-- SECTION 3: schedulers table  (V2, Step 8)
-- ============================================================

CREATE TABLE IF NOT EXISTS schedulers (
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    algorithm  VARCHAR(30)  NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);

-- ============================================================
-- SECTION 4: task_executions — V3 metric columns
-- ============================================================

ALTER TABLE task_executions ADD COLUMN IF NOT EXISTS waiting_time_ms    BIGINT;
ALTER TABLE task_executions ADD COLUMN IF NOT EXISTS turnaround_time_ms BIGINT;

-- ============================================================
-- SECTION 5: Ensure worker_id on task_executions is UUID type
--            (V2, Step 5).  Safe to call if already UUID.
-- ============================================================

DO $$
DECLARE
    col_type TEXT;
BEGIN
    SELECT data_type INTO col_type
    FROM information_schema.columns
    WHERE table_name  = 'task_executions'
      AND column_name = 'worker_id';

    IF col_type IS NOT NULL AND col_type <> 'uuid' THEN
        -- Drop FK before altering type
        ALTER TABLE task_executions DROP CONSTRAINT IF EXISTS task_executions_worker_id_fkey;
        EXECUTE 'ALTER TABLE task_executions ALTER COLUMN worker_id TYPE UUID USING NULL';
    END IF;
END $$;

-- ============================================================
-- SECTION 6: FK task_executions.worker_id -> workers(id)
--            Add only if not already present.
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'task_executions_worker_id_fkey'
    ) THEN
        ALTER TABLE task_executions
            ADD CONSTRAINT task_executions_worker_id_fkey
                FOREIGN KEY (worker_id)
                REFERENCES workers(id)
                ON DELETE SET NULL;
    END IF;
END $$;

-- ============================================================
-- SECTION 7: Scheduler hot-path indexes (V3)
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_tasks_status_arrival
    ON tasks (status, arrival_time ASC);

CREATE INDEX IF NOT EXISTS idx_tasks_status_execution_time
    ON tasks (status, execution_time ASC);

CREATE INDEX IF NOT EXISTS idx_tasks_status_priority
    ON tasks (status, priority DESC);

-- Seed heartbeat for any existing workers so the health checker
-- does not immediately mark them OFFLINE.
UPDATE workers
SET last_heartbeat_at = now()
WHERE last_heartbeat_at IS NULL;
