package com.fidelis.syncbatch.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live integration tests — require the application running on localhost:8080.
 *
 * <p>These tests call the real REST API and trigger actual Spring Batch jobs,
 * generating metrics that Prometheus scrapes and Grafana displays.
 *
 * <p><b>How to run:</b>
 * <pre>
 *   # 1. Start the app (in another terminal):
 *   mvn spring-boot:run -Dspring-boot.run.profiles=dev
 *
 *   # 2. Run only the integration tests:
 *   mvn test -Dgroups=integration
 *
 *   # 3. Open Grafana: http://localhost:3000  (admin/admin)
 *      Dashboards → Sync Batch Data
 * </pre>
 *
 * <p>Each scenario adds data points to the Grafana time series:
 * <ul>
 *   <li>Writes: local → source1, local → source2</li>
 *   <li>Filters: conflict resolution (same or older timestamps)</li>
 *   <li>Reads: incremental pulls with date filter</li>
 *   <li>Load: 5 full rounds producing a visible ramp in the dashboard</li>
 * </ul>
 */
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Live Sync Integration Tests (requires running app on :8080)")
class LiveSyncIntegrationTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String JOBS_EXT_TO_LOCAL = BASE_URL + "/api/jobs/sync/external-to-local";
    private static final String JOBS_LOCAL_TO_EXT = BASE_URL + "/api/jobs/sync/local-to-external";
    private static final String JOBS_STATUS = BASE_URL + "/api/jobs/status/";
    private static final String CLIENTS_URL = BASE_URL + "/api/clients";

    private static final RestTemplate rest = new RestTemplate();
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // Emails created during tests — for cleanup in @AfterAll
    private static final List<String> createdEmails = new ArrayList<>();

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @BeforeAll
    static void requiresRunningApp() {
        boolean appRunning = false;
        try {
            ResponseEntity<String> health = rest.getForEntity(
                BASE_URL + "/actuator/health", String.class);
            appRunning = health.getStatusCode().is2xxSuccessful();
        } catch (ResourceAccessException ignored) {}

        Assumptions.assumeTrue(appRunning,
            "SKIPPED — App is NOT running on localhost:8080. " +
            "Start with: mvn spring-boot:run -Dspring-boot.run.profiles=dev");

        System.out.println("\n✔ App is UP — starting integration scenarios\n");
    }

    @AfterAll
    static void cleanup() {
        System.out.println("\n── Cleanup: removing " + createdEmails.size() + " test clients ──");
        for (String email : createdEmails) {
            try {
                rest.delete(CLIENTS_URL + "/" + email);
            } catch (Exception ignored) {}
        }
    }

    // ── Helper methods ─────────────────────────────────────────────────────────

    private long triggerJob(String url, String source, boolean fullLoad, LocalDateTime dateFrom) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", source);
        body.put("fullLoad", fullLoad);
        if (dateFrom != null) {
            body.put("dateFrom", dateFrom.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = rest.exchange(
            url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsKey("executionId");
        return ((Number) response.getBody().get("executionId")).longValue();
    }

    private String awaitJobCompletion(long executionId) throws InterruptedException {
        String status = "STARTED";
        for (int i = 0; i < 30; i++) {
            Thread.sleep(500);
            try {
                ResponseEntity<Map> res = rest.getForEntity(JOBS_STATUS + executionId, Map.class);
                status = (String) res.getBody().get("status");
                if ("COMPLETED".equals(status) || "FAILED".equals(status)) break;
            } catch (Exception ignored) {}
        }
        return status;
    }

    private void createClient(String email, String name, String document, LocalDateTime lastUpdated) {
        Map<String, Object> client = Map.of(
            "email", email,
            "name", name,
            "document", document == null ? "" : document,
            "lastUpdated", lastUpdated.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            "source", "local"
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        rest.exchange(CLIENTS_URL, HttpMethod.POST, new HttpEntity<>(client, headers), Map.class);
        createdEmails.add(email);
    }

    private void scrapeDelay() throws InterruptedException {
        System.out.println("  ⏱ Waiting 16s for Prometheus scrape...");
        Thread.sleep(16_000);
    }

    private void printMetrics(String label) {
        try {
            ResponseEntity<String> res = rest.getForEntity(
                BASE_URL + "/actuator/prometheus", String.class);
            String body = res.getBody();
            System.out.printf("%n── %s ──%n", label);
            Arrays.stream(body.split("\n"))
                .filter(l -> l.startsWith("sync_items") && !l.startsWith("#"))
                .forEach(System.out::println);
        } catch (Exception e) {
            System.out.println("Could not fetch metrics: " + e.getMessage());
        }
    }

    // ── Scenarios ──────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("1. Seed: push local seed data to source1 and source2")
    void scenario_fullSyncLocalToAllSources() throws InterruptedException {
        System.out.println("\n=== Scenario 1: Full sync Local → source1 and source2 ===");

        long id1 = triggerJob(JOBS_LOCAL_TO_EXT, "source1", true, null);
        String s1 = awaitJobCompletion(id1);
        assertThat(s1).isEqualTo("COMPLETED");
        System.out.printf("  source1: job #%d → %s%n", id1, s1);

        long id2 = triggerJob(JOBS_LOCAL_TO_EXT, "source2", true, null);
        String s2 = awaitJobCompletion(id2);
        assertThat(s2).isEqualTo("COMPLETED");
        System.out.printf("  source2: job #%d → %s%n", id2, s2);

        printMetrics("After Scenario 1");
        scrapeDelay();
    }

    @Test
    @Order(2)
    @DisplayName("2. Conflict resolution: pull source1 → local (all filtered — same timestamps)")
    void scenario_externalToLocal_allFiltered() throws InterruptedException {
        System.out.println("\n=== Scenario 2: source1 → local (conflict resolution, all filtered) ===");

        long id = triggerJob(JOBS_EXT_TO_LOCAL, "source1", true, null);
        String status = awaitJobCompletion(id);
        assertThat(status).isEqualTo("COMPLETED");
        System.out.printf("  job #%d → %s (filter=3 expected)%n", id, status);

        printMetrics("After Scenario 2");
        scrapeDelay();
    }

    @Test
    @Order(3)
    @DisplayName("3. New clients: create 5 new records, then sync to both sources")
    void scenario_newClientsAndSync() throws InterruptedException {
        System.out.println("\n=== Scenario 3: Insert 5 new clients, then sync ===");
        LocalDateTime now = LocalDateTime.now();

        for (int i = 1; i <= 5; i++) {
            String email = "integration-" + i + "@test.com";
            createClient(email, "Integration User " + i,
                String.format("111.222.333-%02d", i),
                now.minusMinutes(5 - i));
        }
        System.out.println("  Created 5 test clients");

        long id1 = triggerJob(JOBS_LOCAL_TO_EXT, "source1", true, null);
        assertThat(awaitJobCompletion(id1)).isEqualTo("COMPLETED");
        System.out.printf("  local→source1: job #%d COMPLETED%n", id1);

        long id2 = triggerJob(JOBS_LOCAL_TO_EXT, "source2", true, null);
        assertThat(awaitJobCompletion(id2)).isEqualTo("COMPLETED");
        System.out.printf("  local→source2: job #%d COMPLETED%n", id2);

        printMetrics("After Scenario 3");
        scrapeDelay();
    }

    @Test
    @Order(4)
    @DisplayName("4. Incremental sync: update 2 clients (newer timestamp), incremental pull")
    void scenario_incrementalSync() throws InterruptedException {
        System.out.println("\n=== Scenario 4: Update 2 clients, incremental sync ===");

        LocalDateTime futureTs = LocalDateTime.now().plusMinutes(1);
        createClient("integration-1@test.com", "Updated User 1", "111.222.333-01", futureTs);
        createClient("integration-2@test.com", "Updated User 2", "111.222.333-02", futureTs);
        System.out.println("  Updated 2 clients with future timestamp");

        // Incremental local→source1: only the 2 updated records should be written
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(30);
        long id = triggerJob(JOBS_LOCAL_TO_EXT, "source1", false, cutoff);
        assertThat(awaitJobCompletion(id)).isEqualTo("COMPLETED");
        System.out.printf("  incremental local→source1: job #%d COMPLETED%n", id);

        // Incremental source1→local: those 2 records now exist in source1 with same ts
        long id2 = triggerJob(JOBS_EXT_TO_LOCAL, "source1", false, cutoff);
        assertThat(awaitJobCompletion(id2)).isEqualTo("COMPLETED");
        System.out.printf("  incremental source1→local: job #%d COMPLETED%n", id2);

        printMetrics("After Scenario 4");
        scrapeDelay();
    }

    @Test
    @Order(5)
    @DisplayName("5. Cross-source: pull source2 → local (filters all, already seeded)")
    void scenario_source2ToLocal() throws InterruptedException {
        System.out.println("\n=== Scenario 5: source2 → local (conflict resolution) ===");

        long id = triggerJob(JOBS_EXT_TO_LOCAL, "source2", true, null);
        assertThat(awaitJobCompletion(id)).isEqualTo("COMPLETED");
        System.out.printf("  source2→local: job #%d COMPLETED%n", id);

        printMetrics("After Scenario 5");
        scrapeDelay();
    }

    @ParameterizedTest(name = "Round {0}")
    @ValueSource(ints = {1, 2, 3, 4, 5})
    @Order(6)
    @DisplayName("6. Load: 5 repeated rounds — builds Grafana time series")
    void scenario_loadRounds(int round) throws InterruptedException {
        System.out.printf("%n=== Load Round %d/5 ===%n", round);

        // Alternate: full sync one direction, then the reverse
        long a = triggerJob(JOBS_LOCAL_TO_EXT, "source1", true, null);
        assertThat(awaitJobCompletion(a)).isEqualTo("COMPLETED");

        long b = triggerJob(JOBS_EXT_TO_LOCAL, "source1", true, null);
        assertThat(awaitJobCompletion(b)).isEqualTo("COMPLETED");

        long c = triggerJob(JOBS_LOCAL_TO_EXT, "source2", true, null);
        assertThat(awaitJobCompletion(c)).isEqualTo("COMPLETED");

        long d = triggerJob(JOBS_EXT_TO_LOCAL, "source2", true, null);
        assertThat(awaitJobCompletion(d)).isEqualTo("COMPLETED");

        System.out.printf("  Round %d: 4 jobs completed (ids: %d, %d, %d, %d)%n",
            round, a, b, c, d);

        printMetrics("Round " + round);
        scrapeDelay(); // allow Prometheus to scrape between rounds
    }

    @Test
    @Order(7)
    @DisplayName("7. Summary: print final metrics state")
    void scenario_summary() {
        System.out.println("\n=== Final Metrics Summary ===");
        printMetrics("FINAL STATE");
        System.out.println("""

            ┌─────────────────────────────────────────┐
            │  Open Grafana: http://localhost:3000     │
            │  Login: admin / admin                   │
            │  Dashboard: Sync Batch Data             │
            └─────────────────────────────────────────┘
            """);
    }
}
