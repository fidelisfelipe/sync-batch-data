package com.fidelis.syncbatch.listener;

import com.fidelis.syncbatch.util.SyncMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

/**
 * Logs step-level metrics (read/write/skip counts) and updates Micrometer gauges.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncStepListener implements StepExecutionListener {

    private final SyncMetrics syncMetrics;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("Step starting: step={} job={}", stepExecution.getStepName(),
                stepExecution.getJobExecution().getJobInstance().getJobName());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        long readCount   = stepExecution.getReadCount();
        long writeCount  = stepExecution.getWriteCount();
        long skipCount   = stepExecution.getSkipCount();
        long filterCount = stepExecution.getFilterCount();

        log.info("Step completed: step={} read={} write={} skip={} filter={} status={}",
                stepExecution.getStepName(), readCount, writeCount, skipCount, filterCount,
                stepExecution.getExitStatus().getExitCode());

        syncMetrics.incrementItemsRead(readCount);
        syncMetrics.incrementItemsWritten(writeCount);

        for (int i = 0; i < skipCount; i++) {
            syncMetrics.incrementItemsSkipped();
        }

        return stepExecution.getExitStatus();
    }
}
