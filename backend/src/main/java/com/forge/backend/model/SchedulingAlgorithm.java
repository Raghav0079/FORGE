package com.forge.backend.model;

/**
 * Scheduling algorithms supported by the FORGE scheduler engine.
 * Mirrors AlgorithmType but used as the canonical enum on the Task entity.
 * AlgorithmType is retained for legacy/external API compatibility.
 */
public enum SchedulingAlgorithm {
    FCFS,
    SJF,
    ROUND_ROBIN,
    PRIORITY,
    MULTILEVEL_QUEUE,
    MULTILEVEL_FEEDBACK_QUEUE
}
