package com.fidelis.syncbatch.reader;

import com.fidelis.syncbatch.model.Client;
import com.fidelis.syncbatch.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;

/**
 * Reads {@link Client} records from the local (primary) datasource.
 * Used in the Local → External sync direction.
 */
@Slf4j
@RequiredArgsConstructor
public class LocalClientReader implements ItemReader<Client> {

    private final ClientRepository clientRepository;
    private final boolean fullLoad;
    private final LocalDateTime since;

    private Iterator<Client> iterator;

    @Override
    public Client read() {
        if (iterator == null) {
            List<Client> clients = loadClients();
            log.info("LocalClientReader: loaded {} records (fullLoad={}, since={})", clients.size(), fullLoad, since);
            iterator = clients.iterator();
        }

        return iterator.hasNext() ? iterator.next() : null;
    }

    private List<Client> loadClients() {
        if (fullLoad) {
            return clientRepository.findAll();
        }
        LocalDateTime cutoff = since != null ? since : LocalDateTime.now().minusDays(1);
        return clientRepository.findModifiedSince(cutoff);
    }
}
