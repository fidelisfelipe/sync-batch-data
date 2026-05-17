package com.fidelis.syncbatch.agent.model;

import java.time.Instant;

/**
 * Structured event parsed from a sync-batch log line.
 */
public record SyncEvent(
        EventType type,
        String jobName,
        String source,
        long readCount,
        long writeCount,
        long filterCount,
        long durationMs,
        String rawMessage,
        Instant timestamp
) {
    public enum EventType {
        JOB_STARTED,
        JOB_COMPLETED,
        JOB_FAILED,
        STEP_COMPLETED,
        FETCH_COMPLETED,
        WRITE_COMPLETED,
        CIRCUIT_BREAKER,
        SCHEMA_INIT,
        ERROR,
        UNKNOWN
    }

    public double throughputPerSec() {
        if (durationMs <= 0) return 0;
        return writeCount / (durationMs / 1000.0);
    }

    public double filterRate() {
        long total = readCount;
        if (total <= 0) return 0;
        return (filterCount * 100.0) / total;
    }
}
