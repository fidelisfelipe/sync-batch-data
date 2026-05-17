package com.fidelis.syncbatch.writer;

import com.fidelis.syncbatch.model.Client;
import com.fidelis.syncbatch.repository.ClientRepository;
import com.fidelis.syncbatch.util.SyncMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.time.LocalDateTime;

/**
 * Idempotent writer for the local (primary) datasource.
 *
 * <p>Uses email as the natural key: updates existing records, inserts new ones.
 * This ensures the job can be re-run safely without creating duplicates.
 */
@Slf4j
@RequiredArgsConstructor
public class LocalClientWriter implements ItemWriter<Client> {

    private final ClientRepository clientRepository;
    private final SyncMetrics syncMetrics;

    @Override
    public void write(Chunk<? extends Client> chunk) {
        log.debug("LocalClientWriter: writing {} items", chunk.size());
        var timer = syncMetrics.startWriteTimer();

        for (Client client : chunk) {
            try {
                upsert(client);
            } catch (Exception e) {
                log.error("Failed to write client email={}: {}", client.getEmail(), e.getMessage(), e);
                throw e; // let Spring Batch handle retry/skip
            }
        }

        syncMetrics.stopWriteTimer(timer);
        log.info("LocalClientWriter: wrote {} items", chunk.size());
    }

    private void upsert(Client client) {
        int updated = clientRepository.updateByEmail(client);
        if (updated == 0) {
            // New record — ensure timestamps are set
            if (client.getLastUpdated() == null) {
                client.setLastUpdated(LocalDateTime.now());
            }
            client.setId(null); // let DB generate PK
            clientRepository.save(client);
            log.debug("Inserted new client: email={}", client.getEmail());
        } else {
            log.debug("Updated existing client: email={}", client.getEmail());
        }
    }
}
