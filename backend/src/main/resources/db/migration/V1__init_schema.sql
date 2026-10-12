-- ============================================================
-- V1__init_schema.sql
-- Initial schema for FORGE backend.
-- Creates: users, tasks, task_executions tables.
-- ============================================================

-- -------------------------------------------------------------
-- ENUM types (PostgreSQL native enums)
-- -------------------------------------------------------------

CREATE TYPE task_status AS ENUM (
    'CREATED',
    'QUEUED',
    'RUNNING',
    'COMPLETED',
    'FAILED',
    'CANCELLED'
);

CREATE TYPE scheduling_algorithm AS ENUM (
    'FCFS',
    'SJF',
    'ROUND_ROBIN',
    'PRIORITY',
    'MULTILEVEL_QUEUE',
    'MULTILEVEL_FEEDBACK_QUEUE'
);

CREATE TYPE user_role AS ENUM (
    'ADMIN',
    'USER'
);

-- -------------------------------------------------------------
-- users
-- -------------------------------------------------------------
CREATE TABLE users (
    id            UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);

-- -------------------------------------------------------------
-- tasks
-- -------------------------------------------------------------
CREATE TABLE tasks (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name                 VARCHAR(100) NOT NULL,
    description          VARCHAR(500),
    payload              TEXT,
    priority             INTEGER      NOT NULL,
    status               VARCHAR(30)  NOT NULL,
    scheduling_algorithm VARCHAR(30)  NOT NULL,
    execution_time       BIGINT       NOT NULL,
    arrival_time         TIMESTAMP    NOT NULL,
    created_at           TIMESTAMP    NOT NULL,
    updated_at           TIMESTAMP    NOT NULL
);

-- -------------------------------------------------------------
-- task_executions
-- -------------------------------------------------------------
CREATE TABLE task_executions (
    id            UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    task_id       UUID         NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    started_at    TIMESTAMP,
    finished_at   TIMESTAMP,
    duration_ms   BIGINT,
    status        VARCHAR(20)  NOT NULL,
    worker_id     UUID,
    error_message VARCHAR(1000),
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

-- Index: quickly look up executions for a given task
CREATE INDEX idx_task_executions_task_id ON task_executions(task_id);

-- Index: quickly look up tasks by status (scheduler hot path)
CREATE INDEX idx_tasks_status ON tasks(status);

-- -------------------------------------------------------------
-- workers
-- -------------------------------------------------------------
CREATE TABLE workers (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'IDLE',
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

-- -------------------------------------------------------------
-- schedulers
-- -------------------------------------------------------------
CREATE TABLE schedulers (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    algorithm   VARCHAR(30)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);
