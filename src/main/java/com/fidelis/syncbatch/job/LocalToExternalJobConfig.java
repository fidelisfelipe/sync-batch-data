package com.fidelis.syncbatch.job;

import com.fidelis.syncbatch.listener.JobCompletionListener;
import com.fidelis.syncbatch.listener.SyncStepListener;
import com.fidelis.syncbatch.model.Client;
import com.fidelis.syncbatch.reader.LocalClientReader;
import com.fidelis.syncbatch.repository.ClientRepository;
import com.fidelis.syncbatch.service.ExternalClientService;
import com.fidelis.syncbatch.util.SyncMetrics;
import com.fidelis.syncbatch.writer.ExternalClientWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Spring Batch Job: Local Database → External Source.
 *
 * <p>Uses {@code @StepScope} beans so readers/writers receive fresh instances
 * with injected job parameters per execution.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class LocalToExternalJobConfig {

    public static final String JOB_NAME  = "localToExternalSyncJob";
    public static final String STEP_NAME = "localToExternalStep";

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

    // ── StepScope beans ───────────────────────────────────────────────────────

    @Bean
    @org.springframework.batch.core.configuration.annotation.StepScope
    public LocalClientReader localClientReader(
            @Value("#{jobParameters['fullLoad']}") String fullLoad,
            @Value("#{jobParameters['dateFrom']}") String dateFrom) {

        boolean isFullLoad = Boolean.parseBoolean(fullLoad);
        LocalDateTime since = (dateFrom != null && !dateFrom.isBlank())
                ? LocalDateTime.parse(dateFrom, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null;

        return new LocalClientReader(clientRepository, isFullLoad, since);
    }

    @Bean
    @org.springframework.batch.core.configuration.annotation.StepScope
    public ItemProcessor<Client, Client> localToExternalProcessor(
            @Value("#{jobParameters['source']}") String source) {
        return client -> {
            client.setSource(source);
            return client;
        };
    }

    @Bean
    @org.springframework.batch.core.configuration.annotation.StepScope
    public ExternalClientWriter externalClientWriter(
            @Value("#{jobParameters['source']}") String source) {
        return new ExternalClientWriter(externalClientService, syncMetrics, source);
    }

    // ── Step & Job ────────────────────────────────────────────────────────────

    @Bean(STEP_NAME)
    public Step localToExternalStep(
            JobRepository jobRepository,
            LocalClientReader localClientReader,
            ItemProcessor<Client, Client> localToExternalProcessor,
            ExternalClientWriter externalClientWriter) {

        return new StepBuilder(STEP_NAME, jobRepository)
                .<Client, Client>chunk(chunkSize, transactionManager)
                .reader(localClientReader)
                .processor(localToExternalProcessor)
                .writer(externalClientWriter)
                .faultTolerant()
                    .skipLimit(skipLimit)
                    .skip(Exception.class)
                    .retryLimit(retryLimit)
                    .retry(org.springframework.dao.TransientDataAccessException.class)
                .listener((StepExecutionListener) syncStepListener)
                .build();
    }

    @Bean(JOB_NAME)
    public Job localToExternalSyncJob(JobRepository jobRepository, Step localToExternalStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(jobCompletionListener)
                .start(localToExternalStep)
                .build();
    }
}
