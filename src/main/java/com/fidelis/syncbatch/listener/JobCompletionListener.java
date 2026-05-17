package com.fidelis.syncbatch.listener;

import com.fidelis.syncbatch.util.SyncMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

/**
 * Listens to job lifecycle events and records metrics + structured logs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobCompletionListener implements JobExecutionListener {

    private final SyncMetrics syncMetrics;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        String source  = jobExecution.getJobParameters().getString("source", "unknown");

        log.info("Job starting: job={} source={} executionId={}",
                jobName, source, jobExecution.getId());

        syncMetrics.incrementActiveJobs();
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        String source  = jobExecution.getJobParameters().getString("source", "unknown");

        BatchStatus status = jobExecution.getStatus();
        long durationMs = jobExecution.getEndTime() != null && jobExecution.getStartTime() != null
                ? java.time.Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime()).toMillis()
                : -1;

        if (status == BatchStatus.COMPLETED) {
            log.info("Job COMPLETED: job={} source={} durationMs={} executionId={}",
                    jobName, source, durationMs, jobExecution.getId());
            syncMetrics.recordJobSuccess(jobName, source);
        } else {
            log.error("Job FAILED: job={} source={} status={} durationMs={} executionId={}",
                    jobName, source, status, durationMs, jobExecution.getId());
            syncMetrics.recordJobFailure(jobName, source);
        }

        syncMetrics.decrementActiveJobs();
    }
}
