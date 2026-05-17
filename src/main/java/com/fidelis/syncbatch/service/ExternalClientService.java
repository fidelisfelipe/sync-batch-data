package com.fidelis.syncbatch.service;

import com.fidelis.syncbatch.model.Client;
import com.fidelis.syncbatch.util.DataSourceContextHolder;
import com.fidelis.syncbatch.util.SyncMetrics;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Abstraction over external datasource access.
 * Wrapped with Circuit Breaker and Retry to handle transient external failures.
 *
 * <p>Uses {@link DataSourceContextHolder} + the routing datasource to route
 * JDBC calls to the correct external datasource at runtime.
 */
@Slf4j
@Service
public class ExternalClientService {

    private final DataSource routingDataSource;
    private final SyncMetrics metrics;

    /**
     * Explicit constructor needed so @Qualifier targets the routing datasource,
     * not the @Primary localDataSource.
     */
    public ExternalClientService(
            @Qualifier("routingDataSource") DataSource routingDataSource,
            SyncMetrics metrics) {
        this.routingDataSource = routingDataSource;
        this.metrics = metrics;
    }

    private static final RowMapper<Client> CLIENT_ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

    /**
     * Fetches all clients from the external source, or only those modified since {@code since}.
     *
     * @param source   routing key (e.g. "source1")
     * @param since    null means full load, otherwise incremental
     */
    @CircuitBreaker(name = "externalClient", fallbackMethod = "fetchClientsFallback")
    @Retry(name = "externalClient")
    public List<Client> fetchClients(String source, LocalDateTime since) {
        log.info("Fetching clients from external source={} since={}", source, since);
        DataSourceContextHolder.setDataSourceKey(source);
        try {
            JdbcTemplate jdbc = new JdbcTemplate(routingDataSource);
            String sql = buildSelectSql(since);
            Object[] params = since != null ? new Object[]{Timestamp.valueOf(since)} : new Object[]{};
            List<Client> clients = jdbc.query(sql, CLIENT_ROW_MAPPER, params);
            log.info("Fetched {} clients from source={}", clients.size(), source);
            return clients;
        } finally {
            DataSourceContextHolder.clearDataSourceKey();
        }
    }

    /**
     * Upserts a client into the external datasource (idempotent).
     *
     * @param source routing key
     * @param client the client to upsert
     */
    @CircuitBreaker(name = "externalClient", fallbackMethod = "upsertClientFallback")
    @Retry(name = "externalClient")
    public void upsertClient(String source, Client client) {
        log.debug("Upserting client email={} to source={}", client.getEmail(), source);
        DataSourceContextHolder.setDataSourceKey(source);
        try {
            JdbcTemplate jdbc = new JdbcTemplate(routingDataSource);

            int updated = jdbc.update(
                "UPDATE CLIENT SET name=?, document=?, last_updated=?, source=? WHERE email=?",
                client.getName(), client.getDocument(),
                Timestamp.valueOf(client.getLastUpdated()), client.getSource(),
                client.getEmail()
            );

            if (updated == 0) {
                jdbc.update(
                    "INSERT INTO CLIENT (name, email, document, last_updated, source) VALUES (?,?,?,?,?)",
                    client.getName(), client.getEmail(), client.getDocument(),
                    Timestamp.valueOf(client.getLastUpdated()), client.getSource()
                );
            }
        } finally {
            DataSourceContextHolder.clearDataSourceKey();
        }
    }

    // ── Fallback methods ──────────────────────────────────────────────────────

    @SuppressWarnings("unused")
    public List<Client> fetchClientsFallback(String source, LocalDateTime since, Throwable ex) {
        log.error("Circuit breaker open for source={}: {}. Returning empty list.", source, ex.getMessage());
        metrics.recordRetry(source);
        return Collections.emptyList();
    }

    @SuppressWarnings("unused")
    public void upsertClientFallback(String source, Client client, Throwable ex) {
        log.error("Circuit breaker open for source={} while upserting email={}: {}",
                source, client.getEmail(), ex.getMessage());
        metrics.recordRetry(source);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildSelectSql(LocalDateTime since) {
        if (since == null) {
            return "SELECT id, name, email, document, last_updated, source FROM CLIENT ORDER BY last_updated ASC";
        }
        return "SELECT id, name, email, document, last_updated, source FROM CLIENT WHERE last_updated > ? ORDER BY last_updated ASC";
    }

    private static Client mapRow(ResultSet rs) throws java.sql.SQLException {
        return Client.builder()
                .id(rs.getLong("id"))
                .name(rs.getString("name"))
                .email(rs.getString("email"))
                .document(rs.getString("document"))
                .lastUpdated(rs.getTimestamp("last_updated").toLocalDateTime())
                .source(rs.getString("source"))
                .build();
    }
}
