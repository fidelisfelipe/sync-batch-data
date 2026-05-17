package com.fidelis.syncbatch.agent.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Point-in-time snapshot of sync performance metrics.
 * Serialized to JSON and pushed to SSE clients every second.
 */
@Data
@Builder
public class PerformanceSnapshot {

    // Cumulative counters
    private long totalRead;
    private long totalWritten;
    private long totalFiltered;
    private long totalSkipped;
    private long jobsCompleted;
    private long jobsFailed;

    // Current performance
    private double throughputPerSec;
    private long avgDurationMs;
    private long lastDurationMs;

    // Source status: source → "IDLE" | "RUNNING" | "COMPLETED" | "FAILED"
    private Map<String, String> sourceStatus;

    // Recent activity
    private Instant lastActivity;
    private boolean idle;

    // Agent output
    private List<AgentComment> recentComments;
    private List<String> recentLogs;
}
