package com.fidelis.syncbatch.util;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom Micrometer metrics for sync jobs.
 * Exposed via /actuator/prometheus for Grafana scraping.
 */
@Slf4j
@Component
public class SyncMetrics {

    private final MeterRegistry registry;

    // Counters
    private final Counter itemsReadCounter;
    private final Counter itemsWrittenCounter;
    private final Counter itemsSkippedCounter;
    private final Counter jobSuccessCounter;
    private final Counter jobFailureCounter;
    private final Counter retryCounter;

    // Gauges (current running jobs)
    private final AtomicLong activeJobs = new AtomicLong(0);

    // Timers
    private final Timer readTimer;
    private final Timer writeTimer;

    public SyncMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.itemsReadCounter = Counter.builder("sync.items.read")
                .description("Total items read from all sources")
                .tag("component", "batch")
                .register(registry);

        this.itemsWrittenCounter = Counter.builder("sync.items.written")
                .description("Total items written to all targets")
                .tag("component", "batch")
                .register(registry);

        this.itemsSkippedCounter = Counter.builder("sync.items.skipped")
                .description("Total items skipped during sync")
                .tag("component", "batch")
                .register(registry);

        this.jobSuccessCounter = Counter.builder("sync.jobs.success")
                .description("Total successful job executions")
                .tag("component", "batch")
                .register(registry);

        this.jobFailureCounter = Counter.builder("sync.jobs.failure")
                .description("Total failed job executions")
                .tag("component", "batch")
                .register(registry);

        this.retryCounter = Counter.builder("sync.retry.count")
                .description("Total retry attempts on external sources")
                .tag("component", "resilience")
                .register(registry);

        Gauge.builder("sync.jobs.active", activeJobs, AtomicLong::get)
                .description("Currently running sync jobs")
                .tag("component", "batch")
                .register(registry);

        this.readTimer = Timer.builder("sync.read.duration")
                .description("Time spent reading items from sources")
                .tag("component", "batch")
                .register(registry);

        this.writeTimer = Timer.builder("sync.write.duration")
                .description("Time spent writing items to targets")
                .tag("component", "batch")
                .register(registry);
    }

    public void incrementItemsRead(long count) {
        itemsReadCounter.increment(count);
    }

    public void incrementItemsWritten(long count) {
        itemsWrittenCounter.increment(count);
    }

    public void incrementItemsSkipped() {
        itemsSkippedCounter.increment();
    }

    public void recordJobSuccess(String jobName, String source) {
        jobSuccessCounter.increment();
        Counter.builder("sync.job.success")
                .tag("job", jobName)
                .tag("source", source)
                .register(registry)
                .increment();
        log.info("Job completed successfully: job={} source={}", jobName, source);
    }

    public void recordJobFailure(String jobName, String source) {
        jobFailureCounter.increment();
        Counter.builder("sync.job.failure")
                .tag("job", jobName)
                .tag("source", source)
                .register(registry)
                .increment();
        log.warn("Job failed: job={} source={}", jobName, source);
    }

    public void recordRetry(String source) {
        retryCounter.increment();
        Counter.builder("sync.retry")
                .tag("source", source)
                .register(registry)
                .increment();
    }

    public void incrementActiveJobs() {
        activeJobs.incrementAndGet();
    }

    public void decrementActiveJobs() {
        activeJobs.decrementAndGet();
    }

    public Timer.Sample startReadTimer() {
        return Timer.start(registry);
    }

    public void stopReadTimer(Timer.Sample sample) {
        sample.stop(readTimer);
    }

    public Timer.Sample startWriteTimer() {
        return Timer.start(registry);
    }

    public void stopWriteTimer(Timer.Sample sample) {
        sample.stop(writeTimer);
    }
}
