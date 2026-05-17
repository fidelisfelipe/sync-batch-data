package com.fidelis.syncbatch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps the {@code datasources.external} block from application.yml into typed beans.
 *
 * <pre>
 * datasources:
 *   external:
 *     source1:
 *       url: jdbc:sqlserver://host:1433;databaseName=mydb
 *       username: user
 *       password: secret
 *       driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "datasources")
public class ExternalDataSourceProperties {

    private Map<String, DataSourceEntry> external = new LinkedHashMap<>();

    @Data
    public static class DataSourceEntry {
        private String url;
        private String username;
        private String password;
        private String driverClassName;
    }
}
