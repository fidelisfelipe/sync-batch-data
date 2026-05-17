package com.fidelis.syncbatch.agent;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fidelis.syncbatch.agent.model.AgentComment;
import com.fidelis.syncbatch.agent.model.PerformanceSnapshot;
import com.fidelis.syncbatch.agent.model.SyncEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Drains the {@link LogEventAppender} queue, parses log lines into
 * {@link SyncEvent}s, updates running metrics, generates agent comments
 * via {@link IntelligentAgent}, and broadcasts snapshots via {@link MonitorBroadcaster}.
 *
 * <p>Runs every second on the Spring task scheduler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PerformanceAnalyzer {

    private final IntelligentAgent agent;
    private final MonitorBroadcaster broadcaster;

    // ── Cumulative counters ────────────────────────────────────────────────────
    private final AtomicLong totalRead      = new AtomicLong();
    private final AtomicLong totalWritten   = new AtomicLong();
    private final AtomicLong totalFiltered  = new AtomicLong();
    private final AtomicLong totalSkipped   = new AtomicLong();
    private final AtomicLong jobsCompleted  = new AtomicLong();
    private final AtomicLong jobsFailed     = new AtomicLong();

    // ── Rolling window (last 30 durations) ───────────────────────────────────
    private final Deque<Long> recentDurations = new ArrayDeque<>(30);
    private double lastThroughput = 0;
    private long   lastDurationMs = 0;

    // ── Per-source state ───────────────────────────────────────────────────────
    private final Map<String, String>  sourceStatus   = new ConcurrentHashMap<>();

    // Cache last step metrics [read, write, filter] per job-type prefix
    private final Map<String, long[]> lastStepData = new ConcurrentHashMap<>();

    // ── Recent history for the UI (ring buffers) ──────────────────────────────
    private final Deque<AgentComment> recentComments = new ArrayDeque<>(50);
    private final Deque<String>       recentLogs     = new ArrayDeque<>(100);

    private volatile Instant lastActivity = Instant.now();

    // ── Log parsing patterns ───────────────────────────────────────────────────
    private static final Pattern JOB_STARTING  = Pattern.compile(
            "Job starting: job=([\\w]+) source=([\\w]+)");
    private static final Pattern JOB_COMPLETED = Pattern.compile(
            "Job (COMPLETED|FAILED): job=([\\w]+) source=([\\w]+) durationMs=(\\d+)");
    private static final Pattern STEP_COMPLETED = Pattern.compile(
            "Step completed: step=([\\w]+) read=(\\d+) write=(\\d+) skip=(\\d+) filter=(\\d+) status=(\\w+)");
    private static final Pattern FETCHED = Pattern.compile(
            "Fetched (\\d+) clients from source=([\\w]+)");
    private static final Pattern WROTE = Pattern.compile(
            "ExternalClientWriter: wrote (\\d+) items to source=([\\w]+)");
    private static final Pattern CIRCUIT_BREAKER = Pattern.compile(
            "Circuit breaker open for source=([\\w]+)");
    private static final Pattern SCHEMA_INIT = Pattern.compile(
            "Schema initialised for external source: ([\\w]+)");

    // ── Scheduled tick ─────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        List<ILoggingEvent> events = LogEventAppender.drain(500);

        for (ILoggingEvent logEvent : events) {
            String msg    = logEvent.getFormattedMessage();
            Level  level  = logEvent.getLevel();
            String logger = logEvent.getLoggerName();

            // Only surface sync-relevant log lines to the UI feed
            if (isRelevant(logger, msg)) {
                addLog(formatLogLine(level, msg));
            }

            SyncEvent syncEvent = parseEvent(msg, level);
            if (syncEvent.type() != SyncEvent.EventType.UNKNOWN) {
                updateMetrics(syncEvent);
                agent.analyze(syncEvent).ifPresent(this::addComment);
            }
        }

        // Idle detection — no events for 60 seconds
        boolean idle = lastActivity.isBefore(Instant.now().minus(60, ChronoUnit.SECONDS));

        broadcaster.broadcast(buildSnapshot(idle));
    }

    // ── Snapshot ───────────────────────────────────────────────────────────────

    public PerformanceSnapshot getSnapshot() {
        boolean idle = lastActivity.isBefore(Instant.now().minus(60, ChronoUnit.SECONDS));
        return buildSnapshot(idle);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private PerformanceSnapshot buildSnapshot(boolean idle) {
        long avgDur = recentDurations.isEmpty() ? 0 :
                (long) recentDurations.stream().mapToLong(Long::longValue).average().orElse(0);

        return PerformanceSnapshot.builder()
                .totalRead(totalRead.get())
                .totalWritten(totalWritten.get())
                .totalFiltered(totalFiltered.get())
                .totalSkipped(totalSkipped.get())
                .jobsCompleted(jobsCompleted.get())
                .jobsFailed(jobsFailed.get())
                .throughputPerSec(lastThroughput)
                .avgDurationMs(avgDur)
                .lastDurationMs(lastDurationMs)
                .sourceStatus(new HashMap<>(sourceStatus))
                .lastActivity(lastActivity)
                .idle(idle)
                .recentComments(new ArrayList<>(recentComments))
                .recentLogs(new ArrayList<>(recentLogs))
                .build();
    }

    private void updateMetrics(SyncEvent event) {
        lastActivity = Instant.now();

        switch (event.type()) {
            case JOB_STARTED -> {
                if (event.source() != null) {
                    sourceStatus.put(event.source(), "RUNNING");
                }
            }
            case JOB_COMPLETED -> {
                jobsCompleted.incrementAndGet();
                if (event.source() != null) {
                    sourceStatus.put(event.source(), "COMPLETED");
                }
                lastDurationMs = event.durationMs();
                if (event.durationMs() > 0) {
                    lastThroughput = event.throughputPerSec();
                    synchronized (recentDurations) {
                        if (recentDurations.size() >= 30) recentDurations.pollFirst();
                        recentDurations.addLast(event.durationMs());
                    }
                }
            }
            case JOB_FAILED -> {
                jobsFailed.incrementAndGet();
                if (event.source() != null) {
                    sourceStatus.put(event.source(), "FAILED");
                }
            }
            case STEP_COMPLETED -> {
                totalRead.addAndGet(event.readCount());
                totalWritten.addAndGet(event.writeCount());
                totalFiltered.addAndGet(event.filterCount());
                totalSkipped.addAndGet(event.filterCount()); // step skips
            }
            default -> {}
        }
    }

    private SyncEvent parseEvent(String msg, Level level) {
        Matcher m;

        if ((m = JOB_STARTING.matcher(msg)).find()) {
            return evt(SyncEvent.EventType.JOB_STARTED, m.group(1), m.group(2), 0, 0, 0, 0, msg);
        }
        if ((m = JOB_COMPLETED.matcher(msg)).find()) {
            boolean failed = "FAILED".equals(m.group(1));
            String jobName = m.group(2);
            long dur = Long.parseLong(m.group(4));
            // Enrich with step data cached from the preceding STEP_COMPLETED event
            String key = jobName.contains("localToExternal") ? "localToExternal" : "externalToLocal";
            long[] step = lastStepData.getOrDefault(key, new long[]{0, 0, 0});
            return evt(failed ? SyncEvent.EventType.JOB_FAILED : SyncEvent.EventType.JOB_COMPLETED,
                    jobName, m.group(3), step[0], step[1], step[2], dur, msg);
        }
        if ((m = STEP_COMPLETED.matcher(msg)).find()) {
            long read   = Long.parseLong(m.group(2));
            long write  = Long.parseLong(m.group(3));
            long filter = Long.parseLong(m.group(5));
            String stepName = m.group(1);
            String key = stepName.contains("localToExternal") ? "localToExternal" : "externalToLocal";
            lastStepData.put(key, new long[]{read, write, filter});
            String jobCtx = stepName.contains("localToExternal") ? "local→external" :
                            stepName.contains("externalToLocal") ? "external→local" : "";
            return evt(SyncEvent.EventType.STEP_COMPLETED, stepName, jobCtx, read, write, filter, 0, msg);
        }
        if ((m = FETCHED.matcher(msg)).find()) {
            return evt(SyncEvent.EventType.FETCH_COMPLETED, null, m.group(2),
                    Long.parseLong(m.group(1)), 0, 0, 0, msg);
        }
        if ((m = WROTE.matcher(msg)).find()) {
            return evt(SyncEvent.EventType.WRITE_COMPLETED, null, m.group(2),
                    0, Long.parseLong(m.group(1)), 0, 0, msg);
        }
        if ((m = CIRCUIT_BREAKER.matcher(msg)).find()) {
            return evt(SyncEvent.EventType.CIRCUIT_BREAKER, null, m.group(1), 0, 0, 0, 0, msg);
        }
        if ((m = SCHEMA_INIT.matcher(msg)).find()) {
            return evt(SyncEvent.EventType.SCHEMA_INIT, null, m.group(1), 0, 0, 0, 0, msg);
        }
        if (level == Level.ERROR &&
                !msg.contains("favicon") && !msg.contains("No static resource") &&
                !msg.contains("Broken pipe") && !msg.contains("Connection reset")) {
            return evt(SyncEvent.EventType.ERROR, null, null, 0, 0, 0, 0, msg);
        }

        return evt(SyncEvent.EventType.UNKNOWN, null, null, 0, 0, 0, 0, msg);
    }

    private SyncEvent evt(SyncEvent.EventType type, String job, String src,
                          long read, long write, long filter, long dur, String msg) {
        return new SyncEvent(type, job, src, read, write, filter, dur, msg, Instant.now());
    }

    private boolean isRelevant(String logger, String msg) {
        if (!logger.startsWith("com.fidelis.syncbatch")) return false;
        // Exclude HTTP noise from GlobalExceptionHandler
        if (logger.contains("GlobalExceptionHandler") ||
            msg.contains("favicon") || msg.contains("No static resource") ||
            msg.contains("Broken pipe") || msg.contains("Connection reset")) return false;
        // Surface sync-relevant messages
        return msg.contains("Job") || msg.contains("Step") || msg.contains("Fetched") ||
               msg.contains("wrote") || msg.contains("Schema") || msg.contains("Circuit") ||
               msg.contains("COMPLETED") || msg.contains("FAILED") ||
               msg.contains("client") || msg.contains("source") || msg.contains("Erro");
    }

    private String formatLogLine(Level level, String msg) {
        String icon = switch (level.levelStr) {
            case "ERROR" -> "🔴";
            case "WARN"  -> "🟡";
            case "DEBUG" -> "🔵";
            default      -> "⚪";
        };
        if (msg.length() > 160) msg = msg.substring(0, 160) + "…";
        return icon + " " + msg;
    }

    private void addComment(AgentComment comment) {
        synchronized (recentComments) {
            if (recentComments.size() >= 50) recentComments.pollFirst();
            recentComments.addLast(comment);
        }
    }

    private void addLog(String line) {
        synchronized (recentLogs) {
            if (recentLogs.size() >= 100) recentLogs.pollFirst();
            recentLogs.addLast(line);
        }
    }
}
