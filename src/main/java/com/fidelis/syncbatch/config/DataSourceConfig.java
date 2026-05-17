package com.fidelis.syncbatch.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Configures the local (primary) datasource and assembles the
 * {@link DynamicRoutingDataSource} that allows batch jobs to switch
 * between external datasources at runtime via {@link com.fidelis.syncbatch.util.DataSourceContextHolder}.
 *
 * <h3>Adding a new database vendor</h3>
 * <ol>
 *   <li>Add the JDBC driver dependency to pom.xml</li>
 *   <li>Add an entry under {@code datasources.external} in application.yml with the appropriate
 *       JDBC URL and driver class (see comments in application.yml for examples)</li>
 *   <li>No code changes required — entries are loaded dynamically</li>
 * </ol>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataSourceConfig {

    private final ExternalDataSourceProperties externalProps;

    /** Local (primary) datasource — used by JPA, Flyway, and Spring Batch infrastructure. */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties localDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "localDataSource")
    @Primary
    public DataSource localDataSource() {
        DataSource ds = localDataSourceProperties()
                .initializeDataSourceBuilder()
                .build();
        log.info("Local datasource initialised: {}", localDataSourceProperties().determineUrl());
        return ds;
    }

    /**
     * Routing datasource used exclusively by batch readers/writers for external sources.
     * The local datasource is also registered so that callers with key="local" work correctly.
     */
    @Bean(name = "routingDataSource")
    public DataSource routingDataSource() {
        DynamicRoutingDataSource routing = new DynamicRoutingDataSource();

        Map<Object, Object> targets = new HashMap<>();
        targets.put("local", localDataSource());

        externalProps.getExternal().forEach((key, entry) -> {
            targets.put(key, buildDataSource(key, entry));
            log.info("External datasource registered: key={} url={}", key, entry.getUrl());
        });

        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(localDataSource());
        routing.afterPropertiesSet();

        return routing;
    }

    private DataSource buildDataSource(String key, ExternalDataSourceProperties.DataSourceEntry entry) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(entry.getUrl());
        config.setUsername(entry.getUsername());
        config.setPassword(entry.getPassword());
        if (entry.getDriverClassName() != null) {
            config.setDriverClassName(entry.getDriverClassName());
        }
        config.setPoolName("HikariPool-" + key);
        config.setMaximumPoolSize(5);
        config.setConnectionTimeout(30_000);
        return new HikariDataSource(config);
    }
}
