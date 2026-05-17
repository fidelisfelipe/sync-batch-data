package com.fidelis.syncbatch.processor;

import com.fidelis.syncbatch.model.Client;
import com.fidelis.syncbatch.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Applies merge/conflict-resolution logic before writing a {@link Client}.
 *
 * <p>Strategy:
 * <ul>
 *   <li>If the incoming record is newer than the existing one → accept</li>
 *   <li>If the incoming record is older or same → skip (return null)</li>
 *   <li>If the record does not exist locally → accept as new</li>
 * </ul>
 *
 * <p>Returning {@code null} from an {@link ItemProcessor} causes Spring Batch to
 * skip that item (no write occurs), which is the idempotency mechanism here.
 */
@Slf4j
@RequiredArgsConstructor
public class ClientSyncProcessor implements ItemProcessor<Client, Client> {

    private final ClientRepository clientRepository;
    private final String targetSource;

    @Override
    @Nullable
    public Client process(Client incoming) {
        Optional<Client> existing = clientRepository.findByEmail(incoming.getEmail());

        if (existing.isEmpty()) {
            log.debug("New client detected: email={}", incoming.getEmail());
            return enrichWithTarget(incoming);
        }

        Client local = existing.get();
        LocalDateTime incomingTs = incoming.getLastUpdated();
        LocalDateTime localTs    = local.getLastUpdated();

        if (incomingTs == null || (localTs != null && !incomingTs.isAfter(localTs))) {
            log.debug("Skipping stale record email={} (incoming={} <= local={})",
                    incoming.getEmail(), incomingTs, localTs);
            return null; // skip
        }

        log.debug("Accepting newer record email={} (incoming={} > local={})",
                incoming.getEmail(), incomingTs, localTs);

        local.setName(incoming.getName());
        local.setDocument(incoming.getDocument());
        local.setLastUpdated(incomingTs);
        local.setSource(targetSource);
        return local;
    }

    private Client enrichWithTarget(Client client) {
        client.setSource(targetSource);
        if (client.getLastUpdated() == null) {
            client.setLastUpdated(LocalDateTime.now());
        }
        return client;
    }
}
