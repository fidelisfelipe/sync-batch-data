package com.fidelis.syncbatch.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Creates the CLIENT schema on each external datasource at startup.
 *
 * <p>In production, external datasources typically already have the table.
 * This initializer ensures the dev/test external H2 databases are usable.
 * Disable with {@code datasources.external.init-schema=false} if needed.
 */
@Slf4j
@Configuration
public class ExternalDataSourceInitializer {

    private final ExternalDataSourceProperties externalProps;
    private final DataSource routingDataSource;

    public ExternalDataSourceInitializer(
            ExternalDataSourceProperties externalProps,
            @org.springframework.beans.factory.annotation.Qualifier("routingDataSource") DataSource routingDataSource) {
        this.externalProps = externalProps;
        this.routingDataSource = routingDataSource;
    }

    private static final String CREATE_SQL = """
        CREATE TABLE IF NOT EXISTS CLIENT (
            id           BIGINT       NOT NULL AUTO_INCREMENT,
            name         VARCHAR(255) NOT NULL,
            email        VARCHAR(255) NOT NULL,
            document     VARCHAR(50),
            last_updated TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
            source       VARCHAR(100) NOT NULL DEFAULT 'external',
            CONSTRAINT pk_client_ext PRIMARY KEY (id),
            CONSTRAINT uq_client_email_ext UNIQUE (email)
        )
        """;

    @Bean
    public ApplicationRunner initExternalSchemas() {
        return args -> {
            externalProps.getExternal().forEach((key, entry) -> {
                com.fidelis.syncbatch.util.DataSourceContextHolder.setDataSourceKey(key);
                try {
                    JdbcTemplate jdbc = new JdbcTemplate(routingDataSource);
                    jdbc.execute(CREATE_SQL);
                    log.info("Schema initialised for external source: {}", key);
                } catch (Exception e) {
                    log.warn("Could not init schema for source={}: {}", key, e.getMessage());
                } finally {
                    com.fidelis.syncbatch.util.DataSourceContextHolder.clearDataSourceKey();
                }
            });
        };
    }
}
