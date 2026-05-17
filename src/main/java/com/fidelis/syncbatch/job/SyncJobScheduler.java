package com.fidelis.syncbatch.job;

import com.fidelis.syncbatch.config.ExternalDataSourceProperties;
import com.fidelis.syncbatch.model.SyncRequest;
import com.fidelis.syncbatch.service.SyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Scheduled trigger for periodic bidirectional sync.
 * Only active when {@code batch.sync.enabled=true}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "batch.sync.enabled", havingValue = "true")
public class SyncJobScheduler {

    private final SyncService syncService;
    private final ExternalDataSourceProperties externalDataSourceProperties;

    @Scheduled(cron = "${batch.sync.cron:0 0 */2 * * ?}")
    public void scheduledExternalToLocal() {
        var sources = externalDataSourceProperties.getExternal();

        if (sources == null || sources.isEmpty()) {
            log.warn("No external sources configured; skipping scheduled sync");
            return;
        }

        log.info("Scheduled External→Local sync triggered for {} source(s)", sources.size());

        sources.keySet().forEach(source -> {
            try {
                SyncRequest request = SyncRequest.builder()
                        .source(source)
                        .fullLoad(false)
                        // 3-hour overlap to tolerate clock skew and transient failures
                        .dateFrom(LocalDateTime.now().minusHours(3))
                        .build();
                long execId = syncService.triggerExternalToLocal(request);
                log.info("Scheduled job launched: source={} executionId={}", source, execId);
            } catch (Exception e) {
                log.error("Scheduled sync failed for source={}: {}", source, e.getMessage(), e);
            }
        });
    }
}
