package com.fidelis.syncbatch.repository;

import com.fidelis.syncbatch.model.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ClientRepositoryTest {

    @Autowired
    private ClientRepository clientRepository;

    private Client alice;
    private Client bob;

    @BeforeEach
    void setUp() {
        clientRepository.deleteAll();

        alice = clientRepository.save(Client.builder()
                .name("Alice")
                .email("alice@test.com")
                .document("111.111.111-11")
                .lastUpdated(LocalDateTime.now().minusDays(5))
                .source("source1")
                .build());

        bob = clientRepository.save(Client.builder()
                .name("Bob")
                .email("bob@test.com")
                .document("222.222.222-22")
                .lastUpdated(LocalDateTime.now().minusDays(1))
                .source("local")
                .build());
    }

    @Test
    void findByEmail_existingEmail_shouldReturnClient() {
        Optional<Client> result = clientRepository.findByEmail("alice@test.com");
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Alice");
    }

    @Test
    void findByEmail_unknownEmail_shouldReturnEmpty() {
        assertThat(clientRepository.findByEmail("nobody@test.com")).isEmpty();
    }

    @Test
    void existsByEmail_shouldReturnTrue_forExistingEmail() {
        assertThat(clientRepository.existsByEmail("bob@test.com")).isTrue();
        assertThat(clientRepository.existsByEmail("ghost@test.com")).isFalse();
    }

    @Test
    void findModifiedSince_shouldReturnOnlyRecentRecords() {
        // cutoff is 2 days ago; alice was last_updated 5 days ago, bob 1 day ago
        LocalDateTime twoDaysAgo = LocalDateTime.now().minusDays(2);
        List<Client> recent = clientRepository.findModifiedSince(twoDaysAgo);

        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).getEmail()).isEqualTo("bob@test.com");
    }

    @Test
    void findModifiedSince_fullRange_shouldReturnAll() {
        LocalDateTime longAgo = LocalDateTime.now().minusYears(1);
        assertThat(clientRepository.findModifiedSince(longAgo)).hasSize(2);
    }

    @Test
    void findBySourceAndLastUpdatedAfter_shouldFilterBySource() {
        LocalDateTime oneWeekAgo = LocalDateTime.now().minusWeeks(1);
        List<Client> fromSource1 = clientRepository.findBySourceAndLastUpdatedAfter("source1", oneWeekAgo);

        assertThat(fromSource1).hasSize(1);
        assertThat(fromSource1.get(0).getEmail()).isEqualTo("alice@test.com");
    }

    @Test
    void updateByEmail_shouldUpdateFieldsWithoutChangingId() {
        alice.setName("Alice Updated");
        alice.setLastUpdated(LocalDateTime.now());

        int updated = clientRepository.updateByEmail(alice);

        assertThat(updated).isEqualTo(1);

        Client reloaded = clientRepository.findByEmail("alice@test.com").orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Alice Updated");
        assertThat(reloaded.getId()).isEqualTo(alice.getId()); // id unchanged
    }

    @Test
    void updateByEmail_unknownEmail_shouldReturnZero() {
        Client ghost = Client.builder()
                .email("ghost@test.com")
                .name("Ghost")
                .lastUpdated(LocalDateTime.now())
                .source("local")
                .build();

        int updated = clientRepository.updateByEmail(ghost);
        assertThat(updated).isEqualTo(0);
    }

    @Test
    void save_shouldPersistAndAssignId() {
        Client carol = clientRepository.save(Client.builder()
                .name("Carol")
                .email("carol@test.com")
                .lastUpdated(LocalDateTime.now())
                .source("local")
                .build());

        assertThat(carol.getId()).isNotNull().isPositive();
    }

    @Test
    void findAll_shouldReturnAllRecords() {
        assertThat(clientRepository.findAll()).hasSize(2);
    }
}
