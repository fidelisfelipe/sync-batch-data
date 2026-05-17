package com.fidelis.syncbatch.reader;

import com.fidelis.syncbatch.model.Client;
import com.fidelis.syncbatch.service.ExternalClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;

/**
 * Reads {@link Client} records from an external datasource.
 *
 * <p>Fetches a full snapshot or an incremental delta (records modified after {@code since})
 * on first call, then serves items one-by-one until exhausted.
 *
 * <p>Not thread-safe by design — each job step should use its own instance.
 */
@Slf4j
public class ExternalClientReader implements ItemReader<Client> {

    private final ExternalClientService externalClientService;
    private final String source;
    private final boolean fullLoad;
    private final LocalDateTime since;

    private Iterator<Client> iterator;

    public ExternalClientReader(ExternalClientService externalClientService,
                                String source,
                                boolean fullLoad,
                                LocalDateTime since) {
        this.externalClientService = externalClientService;
        this.source = source;
        this.fullLoad = fullLoad;
        this.since = since;
    }

    @Override
    public Client read() {
        if (iterator == null) {
            LocalDateTime effectiveSince = fullLoad ? null : since;
            log.info("ExternalClientReader: loading from source={} fullLoad={} since={}", source, fullLoad, effectiveSince);
            List<Client> clients = externalClientService.fetchClients(source, effectiveSince);
            log.info("ExternalClientReader: loaded {} records", clients.size());
            iterator = clients.iterator();
        }

        if (iterator.hasNext()) {
            Client client = iterator.next();
            log.debug("ExternalClientReader: reading email={}", client.getEmail());
            return client;
        }

        return null; // signals end-of-data to Spring Batch
    }
}
