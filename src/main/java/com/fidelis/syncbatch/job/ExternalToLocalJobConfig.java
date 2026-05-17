package com.fidelis.syncbatch.job;

import com.fidelis.syncbatch.listener.JobCompletionListener;
import com.fidelis.syncbatch.listener.SyncStepListener;
import com.fidelis.syncbatch.model.Client;
import com.fidelis.syncbatch.processor.ClientSyncProcessor;
import com.fidelis.syncbatch.reader.ExternalClientReader;
import com.fidelis.syncbatch.repository.ClientRepository;
import com.fidelis.syncbatch.service.ExternalClientService;
import com.fidelis.syncbatch.util.SyncMetrics;
import com.fidelis.syncbatch.writer.LocalClientWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.context.annotation.Scope;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Spring Batch Job: External Source → Local Database.
 *
 * <p>Readers/Processors/Writers are {@code @StepScope} beans so they receive
 * fresh instances (and injected job parameters) for every step execution.
 *
 * <p>Parameters accepted via {@link JobParameters}:
 * <ul>
 *   <li>{@code source}    – routing key of the external datasource (required)</li>
 *   <li>{@code fullLoad}  – "true" for full sync, "false" for incremental</li>
 *   <li>{@code dateFrom}  – ISO datetime; lower bound for incremental sync</li>
 *   <li>{@code timestamp} – unique run ID (added automatically by SyncService)</li>
 * </ul>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ExternalToLocalJobConfig {

    public static final String JOB_NAME  = "externalToLocalSyncJob";
    public static final String STEP_NAME = "externalToLocalStep";

    private final ExternalClientService externalClientService;
    private final ClientRepository clientRepository;
    private final SyncMetrics syncMetrics;
    private final JobCompletionListener jobCompletionListener;
    private final SyncStepListener syncStepListener;
    private final PlatformTransactionManager transactionManager;

    @Value("${batch.sync.chunk-size:100}")
    private int chunkSize;

    @Value("${batch.sync.skip-limit:10}")
    private int skipLimit;

    @Value("${batch.sync.retry-limit:3}")
    private int retryLimit;

    // ── StepScope beans (fresh instance per step execution) ──────────────────

    @Bean
    @org.springframework.batch.core.configuration.annotation.StepScope
    public ExternalClientReader externalClientReader(
            @Value("#{jobParameters['source']}") String source,
            @Value("#{jobParameters['fullLoad']}") String fullLoad,
            @Value("#{jobParameters['dateFrom']}") String dateFrom) {

        boolean isFullLoad = Boolean.parseBoolean(fullLoad);
        LocalDateTime since = (dateFrom != null && !dateFrom.isBlank())
                ? LocalDateTime.parse(dateFrom, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null;

        log.debug("Creating ExternalClientReader: source={} fullLoad={} since={}", source, isFullLoad, since);
        return new ExternalClientReader(externalClientService, source, isFullLoad, since);
    }

    @Bean
    @org.springframework.batch.core.configuration.annotation.StepScope
    public ClientSyncProcessor externalToLocalProcessor() {
        return new ClientSyncProcessor(clientRepository, "local");
    }

    @Bean
    @org.springframework.batch.core.configuration.annotation.StepScope
    public LocalClientWriter localClientWriter() {
        return new LocalClientWriter(clientRepository, syncMetrics);
    }

    // ── Step & Job ────────────────────────────────────────────────────────────

    @Bean(STEP_NAME)
    public Step externalToLocalStep(
            JobRepository jobRepository,
            ExternalClientReader externalClientReader,
            ClientSyncProcessor externalToLocalProcessor,
            LocalClientWriter localClientWriter) {

        return new StepBuilder(STEP_NAME, jobRepository)
                .<Client, Client>chunk(chunkSize, transactionManager)
                .reader(externalClientReader)
                .processor(externalToLocalProcessor)
                .writer(localClientWriter)
                .faultTolerant()
                    .skipLimit(skipLimit)
                    .skip(Exception.class)
                    .retryLimit(retryLimit)
                    .retry(org.springframework.dao.TransientDataAccessException.class)
                .listener((StepExecutionListener) syncStepListener)
                .build();
    }

    @Bean(JOB_NAME)
    public Job externalToLocalSyncJob(JobRepository jobRepository, Step externalToLocalStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(jobCompletionListener)
                .start(externalToLocalStep)
                .build();
    }
}
