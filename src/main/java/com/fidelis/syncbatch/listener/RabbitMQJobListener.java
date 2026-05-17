package com.fidelis.syncbatch.listener;

import com.fidelis.syncbatch.config.RabbitMQConfig;
import com.fidelis.syncbatch.model.SyncRequest;
import com.fidelis.syncbatch.service.SyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listens to {@code sync.trigger.queue} and launches the appropriate sync job.
 *
 * <p>Message payload is a JSON-serialized {@link SyncRequest}.
 * Example message:
 * <pre>{@code
 * {"source":"source1","fullLoad":false,"dateFrom":"2026-01-01T00:00:00"}
 * }</pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMQJobListener {

    private final SyncService syncService;

    @RabbitListener(queues = RabbitMQConfig.SYNC_QUEUE,
                    containerFactory = "rabbitListenerContainerFactory")
    public void onSyncMessage(SyncRequest request) {
        log.info("Received sync message from queue: source={} fullLoad={} dateFrom={}",
                request.getSource(), request.isFullLoad(), request.getDateFrom());

        try {
            syncService.triggerExternalToLocal(request);
        } catch (Exception e) {
            log.error("Failed to process sync message for source={}: {}",
                    request.getSource(), e.getMessage(), e);
            // rethrowing causes Spring AMQP to nack and route to dead-letter queue
            throw new RuntimeException("Sync job failed for source: " + request.getSource(), e);
        }
    }
}
