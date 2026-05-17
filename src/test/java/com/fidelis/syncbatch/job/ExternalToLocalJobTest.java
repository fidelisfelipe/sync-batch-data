package com.fidelis.syncbatch.job;

import com.fidelis.syncbatch.model.Client;
import com.fidelis.syncbatch.model.SyncRequest;
import com.fidelis.syncbatch.repository.ClientRepository;
import com.fidelis.syncbatch.service.ExternalClientService;
import com.fidelis.syncbatch.service.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration test for the External → Local batch job flow.
 *
 * <p>Starts the full Spring context (without RabbitMQ broker) and runs
 * real Spring Batch jobs using mocked external data.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "batch.sync.enabled=false")
class ExternalToLocalJobTest {

    @Autowired private ClientRepository clientRepository;
    @Autowired private SyncService syncService;
    @Autowired private JobRepository jobRepository;

    @MockBean private ExternalClientService externalClientService;
    @MockBean private ConnectionFactory connectionFactory;
    @MockBean private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        clientRepository.deleteAll();
    }

    @Test
    void externalToLocalJob_shouldCompleteAndPersistClient() {
        LocalDateTime ts = LocalDateTime.now().minusHours(1);
        Client externalClient = Client.builder()
                .email("external@test.com")
                .name("External User")
                .document("999.999.999-99")
                .lastUpdated(ts)
                .source("source1")
                .build();

        when(externalClientService.fetchClients(eq("source1"), any()))
                .thenReturn(List.of(externalClient));

        long executionId = syncService.triggerExternalToLocal(
            SyncRequest.builder().source("source1").fullLoad(true).build());

        assertThat(executionId).isPositive();
        assertThat(clientRepository.findByEmail("external@test.com")).isPresent();
    }

    @Test
    void externalToLocalJob_emptySource_shouldCompleteCleanly() {
        when(externalClientService.fetchClients(any(), any())).thenReturn(List.of());

        long executionId = syncService.triggerExternalToLocal(
            SyncRequest.builder().source("source1").fullLoad(false)
                .dateFrom(LocalDateTime.now().minusMinutes(30)).build());

        assertThat(executionId).isPositive();
        assertThat(clientRepository.findAll()).isEmpty();
    }

    @Test
    void externalToLocalJob_multipleClients_allPersisted() {
        List<Client> clients = List.of(
            Client.builder().email("a@test.com").name("A")
                .lastUpdated(LocalDateTime.now()).source("source1").build(),
            Client.builder().email("b@test.com").name("B")
                .lastUpdated(LocalDateTime.now()).source("source1").build(),
            Client.builder().email("c@test.com").name("C")
                .lastUpdated(LocalDateTime.now()).source("source1").build()
        );

        when(externalClientService.fetchClients(any(), any())).thenReturn(clients);

        syncService.triggerExternalToLocal(
            SyncRequest.builder().source("source1").fullLoad(true).build());

        assertThat(clientRepository.count()).isEqualTo(3);
    }
}
