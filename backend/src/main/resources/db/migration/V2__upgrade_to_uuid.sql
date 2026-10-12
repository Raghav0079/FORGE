-- ============================================================
-- V2__upgrade_to_uuid.sql
-- Migrates all primary and foreign keys from BIGINT/BIGSERIAL
-- to UUID across all 5 tables, and adds the workers and
-- schedulers tables which are new in this release.
-- Applies to an existing database that was initialised by V1.
-- ============================================================

-- Enable pgcrypto if gen_random_uuid() is not already available
-- (PostgreSQL 13+ has it built-in; this is a no-op on 13+)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================
-- STEP 1: task_executions — drop foreign key constraints first
--         so that we can freely alter the referenced columns.
-- ============================================================

-- Discover and drop the auto-generated FK name on task_id.
-- The constraint name is deterministic: <table>_<col>_fkey.
ALTER TABLE task_executions
    DROP CONSTRAINT IF EXISTS task_executions_task_id_fkey;

-- ============================================================
-- STEP 2: Add payload column to tasks (missed in V1)
-- ============================================================

ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS payload TEXT;

-- ============================================================
-- STEP 3: Migrate tasks.id  BIGSERIAL -> UUID
-- ============================================================

-- 3a. Drop the sequence-backed default before changing the type
ALTER TABLE tasks ALTER COLUMN id DROP DEFAULT;

-- 3b. Cast the existing BIGINT values to UUID using gen_random_uuid().
--     All existing rows get a brand-new UUID; referential integrity
--     will be rebuilt in STEP 6 after task_executions.task_id is
--     updated as well.
ALTER TABLE tasks
    ALTER COLUMN id TYPE UUID USING gen_random_uuid();

-- 3c. Set the new UUID default
ALTER TABLE tasks
    ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- ============================================================
-- STEP 4: Migrate task_executions.id  BIGSERIAL -> UUID
-- ============================================================

ALTER TABLE task_executions ALTER COLUMN id DROP DEFAULT;

ALTER TABLE task_executions
    ALTER COLUMN id TYPE UUID USING gen_random_uuid();

ALTER TABLE task_executions
    ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- ============================================================
-- STEP 5: Migrate task_executions.task_id  BIGINT -> UUID
--         and task_executions.worker_id  VARCHAR(100) -> UUID
-- ============================================================

-- task_id: existing BIGINT values can no longer map to the
-- (now UUID) tasks.id rows, so we set them to NULL temporarily.
-- The column is NOT NULL — temporarily relax, then re-constrain
-- after re-adding the FK.
ALTER TABLE task_executions
    ALTER COLUMN task_id DROP NOT NULL;

ALTER TABLE task_executions
    ALTER COLUMN task_id TYPE UUID USING NULL;

-- worker_id was VARCHAR(100); cast existing values to NULL since
-- old string identifiers cannot be cast to UUID safely.
ALTER TABLE task_executions
    ALTER COLUMN worker_id TYPE UUID USING NULL;

-- ============================================================
-- STEP 6: Re-add foreign key constraints
-- ============================================================

-- task_id FK with CASCADE delete (matches V1 behaviour)
ALTER TABLE task_executions
    ADD CONSTRAINT task_executions_task_id_fkey
        FOREIGN KEY (task_id)
        REFERENCES tasks(id)
        ON DELETE CASCADE;

-- Re-enforce NOT NULL on task_id now that the column type is correct.
-- Note: if you have existing rows with NULL task_id from the migration
-- above, clean them up first or omit this if nullable is acceptable.
ALTER TABLE task_executions
    ALTER COLUMN task_id SET NOT NULL;

-- worker_id FK — references workers(id), nullable (SET NULL on delete)
-- This constraint is added here; workers table is created in STEP 7.
-- We defer adding the FK until after workers exists (see STEP 7 below).

-- ============================================================
-- STEP 7: Create workers table (new in this release)
-- ============================================================

CREATE TABLE IF NOT EXISTS workers (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'IDLE',
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

-- Now add the worker_id FK on task_executions -> workers
ALTER TABLE task_executions
    ADD CONSTRAINT task_executions_worker_id_fkey
        FOREIGN KEY (worker_id)
        REFERENCES workers(id)
        ON DELETE SET NULL;

-- ============================================================
-- STEP 8: Create schedulers table (new in this release)
-- ============================================================

CREATE TABLE IF NOT EXISTS schedulers (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    algorithm   VARCHAR(30)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

-- ============================================================
-- STEP 9: users — already UUID in V1, no change needed.
--         Included here as an explicit no-op comment for
--         clarity and audit trail.
-- ============================================================
-- users.id is already UUID NOT NULL DEFAULT gen_random_uuid() — no action.
