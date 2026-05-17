package com.fidelis.syncbatch.writer;

import com.fidelis.syncbatch.model.Client;
import com.fidelis.syncbatch.service.ExternalClientService;
import com.fidelis.syncbatch.util.SyncMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

/**
 * Writes {@link Client} records to an external datasource.
 * Delegates to {@link ExternalClientService} which wraps calls with Circuit Breaker + Retry.
 */
@Slf4j
@RequiredArgsConstructor
public class ExternalClientWriter implements ItemWriter<Client> {

    private final ExternalClientService externalClientService;
    private final SyncMetrics syncMetrics;
    private final String targetSource;

    @Override
    public void write(Chunk<? extends Client> chunk) {
        log.debug("ExternalClientWriter: writing {} items to source={}", chunk.size(), targetSource);
        var timer = syncMetrics.startWriteTimer();

        for (Client client : chunk) {
            try {
                externalClientService.upsertClient(targetSource, client);
            } catch (Exception e) {
                log.error("Failed to write client email={} to source={}: {}",
                        client.getEmail(), targetSource, e.getMessage(), e);
                throw e;
            }
        }

        syncMetrics.stopWriteTimer(timer);
        log.info("ExternalClientWriter: wrote {} items to source={}", chunk.size(), targetSource);
    }
}
