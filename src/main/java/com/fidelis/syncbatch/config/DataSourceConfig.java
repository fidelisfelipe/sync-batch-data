package com.fidelis.syncbatch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource() {
        // Implement DynamicRoutingDataSource here in next step
        return null; // placeholder
    }
}