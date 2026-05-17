package com.fidelis.syncbatch.service;

import com.fidelis.syncbatch.model.SyncRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * Orchestrates job launches from REST controller, RabbitMQ listener, and scheduler.
 * Builds {@link JobParameters} from the incoming {@link SyncRequest} and delegates to
 * Spring Batch's {@link JobLauncher}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    private final JobLauncher jobLauncher;
    private final JobRepository jobRepository;
    private final JobExplorer jobExplorer;
    private final Job externalToLocalSyncJob;
    private final Job localToExternalSyncJob;

    /**
     * Launches the External → Local job with the given request parameters.
     *
     * @return JobExecution ID
     */
    public long triggerExternalToLocal(SyncRequest request) {
        validateRequest(request);
        JobParameters params = buildParams(request);
        return launchJob(externalToLocalSyncJob, params);
    }

    /**
     * Launches the Local → External job with the given request parameters.
     *
     * @return JobExecution ID
     */
    public long triggerLocalToExternal(SyncRequest request) {
        validateRequest(request);
        JobParameters params = buildParams(request);
        return launchJob(localToExternalSyncJob, params);
    }

    public JobExecution getJobExecution(long jobExecutionId) {
        return jobExplorer.getJobExecution(jobExecutionId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private long launchJob(Job job, JobParameters params) {
        try {
            log.info("Launching job={} params={}", job.getName(), params);
            JobExecution execution = jobLauncher.run(job, params);
            log.info("Job launched: id={} status={}", execution.getId(), execution.getStatus());
            return execution.getId();
        } catch (Exception e) {
            log.error("Failed to launch job={}: {}", job.getName(), e.getMessage(), e);
            throw new RuntimeException("Failed to launch job: " + job.getName(), e);
        }
    }

    private JobParameters buildParams(SyncRequest request) {
        JobParametersBuilder builder = new JobParametersBuilder()
                .addString("source", request.getSource())
                .addString("fullLoad", String.valueOf(request.isFullLoad()))
                // Unique timestamp ensures each trigger creates a new job instance
                .addLong("timestamp", System.currentTimeMillis());

        if (request.getDateFrom() != null) {
            builder.addString("dateFrom",
                    request.getDateFrom().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        return builder.toJobParameters();
    }

    private void validateRequest(SyncRequest request) {
        if (request.getSource() == null || request.getSource().isBlank()) {
            throw new IllegalArgumentException("'source' parameter is required");
        }
    }
}
