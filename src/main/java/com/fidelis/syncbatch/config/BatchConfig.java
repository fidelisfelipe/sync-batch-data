package com.fidelis.syncbatch.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Marker configuration for Spring Batch customisations.
 *
 * <p>We intentionally do NOT extend {@code DefaultBatchConfiguration} nor use
 * {@code @EnableBatchProcessing} so that Spring Boot's {@code BatchAutoConfiguration}
 * remains active and handles:
 * <ul>
 *   <li>Schema initialisation (controlled by {@code spring.batch.jdbc.initialize-schema})</li>
 *   <li>JobRepository wired to the {@code @Primary} localDataSource</li>
 *   <li>JobLauncher bean</li>
 * </ul>
 *
 * <p>Job/Step beans are declared in {@link ExternalToLocalJobConfig} and
 * {@link LocalToExternalJobConfig} using Spring Batch 5's builder API.
 */
@Configuration
@EnableScheduling
public class BatchConfig {
}
