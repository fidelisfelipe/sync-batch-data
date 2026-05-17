package com.fidelis.syncbatch.controller;

import com.fidelis.syncbatch.model.Client;
import com.fidelis.syncbatch.repository.ClientRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * CRUD REST API for the local CLIENT table.
 *
 * <p>Used to seed test data and simulate real business operations
 * (new/updated records) before triggering sync jobs.
 */
@Slf4j
@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
@Tag(name = "Clients", description = "Manage local client records")
public class ClientController {

    private final ClientRepository repository;

    @GetMapping
    @Operation(summary = "List all local clients")
    public List<Client> findAll() {
        return repository.findAll();
    }

    @PostMapping
    @Transactional
    @Operation(summary = "Create or update a client by email (upsert)")
    public ResponseEntity<Client> upsert(@RequestBody @Valid Client client) {
        if (client.getLastUpdated() == null) {
            client.setLastUpdated(LocalDateTime.now());
        }
        if (client.getSource() == null || client.getSource().isBlank()) {
            client.setSource("local");
        }

        int updated = repository.updateByEmail(client);
        Client saved;
        if (updated == 0) {
            client.setId(null);
            saved = repository.save(client);
            log.info("Created client: email={}", saved.getEmail());
        } else {
            saved = repository.findByEmail(client.getEmail()).orElseThrow();
            log.info("Updated client: email={}", saved.getEmail());
        }
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{email}")
    @Transactional
    @Operation(summary = "Delete a client by email")
    public ResponseEntity<Map<String, String>> delete(@PathVariable String email) {
        return repository.findByEmail(email)
                .map(c -> {
                    repository.delete(c);
                    log.info("Deleted client: email={}", email);
                    return ResponseEntity.ok(Map.of("deleted", email));
                })
                .orElse(ResponseEntity.notFound().<Map<String, String>>build());
    }

    @GetMapping("/count")
    @Operation(summary = "Count local clients")
    public Map<String, Long> count() {
        return Map.of("count", repository.count());
    }
}
