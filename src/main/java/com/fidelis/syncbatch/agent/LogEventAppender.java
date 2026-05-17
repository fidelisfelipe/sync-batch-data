package com.fidelis.syncbatch.agent;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Logback appender that enqueues log events for the monitoring agent.
 *
 * <p>Registered in logback-spring.xml on the com.fidelis.syncbatch logger.
 * Spring beans drain the queue via {@link #drain()} on a schedule.
 *
 * <p>The static queue is intentional — Logback initialises before the Spring
 * context, so we cannot inject beans here. Instead, the queue acts as a
 * decoupled channel between the logging subsystem and the Spring world.
 */
public class LogEventAppender extends AppenderBase<ILoggingEvent> {

    private static final LinkedBlockingQueue<ILoggingEvent> QUEUE =
            new LinkedBlockingQueue<>(8_000);

    @Override
    protected void append(ILoggingEvent event) {
        // Non-blocking: drop the event if the queue is full rather than blocking the caller
        QUEUE.offer(event);
    }

    /**
     * Drains up to {@code maxItems} events from the queue.
     * Called by {@link PerformanceAnalyzer} on its scheduled tick.
     */
    public static List<ILoggingEvent> drain(int maxItems) {
        List<ILoggingEvent> batch = new ArrayList<>(maxItems);
        QUEUE.drainTo(batch, maxItems);
        return batch;
    }

    public static int pendingCount() {
        return QUEUE.size();
    }
}
