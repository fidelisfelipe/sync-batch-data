package com.fidelis.syncbatch.processor;

import com.fidelis.syncbatch.model.Client;
import com.fidelis.syncbatch.repository.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientSyncProcessorTest {

    @Mock
    private ClientRepository clientRepository;

    private ClientSyncProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ClientSyncProcessor(clientRepository, "local");
    }

    @Test
    void process_newClient_shouldReturnEnrichedClient() throws Exception {
        Client incoming = Client.builder()
                .email("new@example.com")
                .name("New User")
                .lastUpdated(LocalDateTime.now())
                .build();

        when(clientRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

        Client result = processor.process(incoming);

        assertThat(result).isNotNull();
        assertThat(result.getSource()).isEqualTo("local");
        assertThat(result.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void process_newerIncomingRecord_shouldReturnUpdatedClient() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime older = now.minusHours(1);

        Client existing = Client.builder()
                .id(1L)
                .email("user@example.com")
                .name("Old Name")
                .lastUpdated(older)
                .source("source1")
                .build();

        Client incoming = Client.builder()
                .email("user@example.com")
                .name("New Name")
                .lastUpdated(now)
                .source("source1")
                .build();

        when(clientRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existing));

        Client result = processor.process(incoming);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getLastUpdated()).isEqualTo(now);
        assertThat(result.getSource()).isEqualTo("local");
    }

    @Test
    void process_staleIncomingRecord_shouldReturnNull() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime older = now.minusHours(1);

        Client existing = Client.builder()
                .id(1L)
                .email("user@example.com")
                .name("Current Name")
                .lastUpdated(now)
                .build();

        Client incoming = Client.builder()
                .email("user@example.com")
                .name("Old Name")
                .lastUpdated(older)
                .build();

        when(clientRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existing));

        Client result = processor.process(incoming);

        assertThat(result).isNull(); // should be skipped
    }

    @Test
    void process_sameTimestamp_shouldReturnNull() throws Exception {
        LocalDateTime ts = LocalDateTime.of(2026, 5, 1, 12, 0, 0);

        Client existing = Client.builder()
                .id(1L).email("user@example.com").name("Name").lastUpdated(ts).build();

        Client incoming = Client.builder()
                .email("user@example.com").name("Same").lastUpdated(ts).build();

        when(clientRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existing));

        assertThat(processor.process(incoming)).isNull();
    }

    @Test
    void process_nullTimestampOnNewClient_shouldSetTimestamp() throws Exception {
        Client incoming = Client.builder()
                .email("no-ts@example.com")
                .name("No Timestamp")
                .lastUpdated(null)
                .build();

        when(clientRepository.findByEmail("no-ts@example.com")).thenReturn(Optional.empty());

        Client result = processor.process(incoming);

        assertThat(result).isNotNull();
        assertThat(result.getLastUpdated()).isNotNull();
    }
}
