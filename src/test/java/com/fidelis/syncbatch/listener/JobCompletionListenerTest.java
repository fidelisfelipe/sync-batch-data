package com.fidelis.syncbatch.listener;

import com.fidelis.syncbatch.util.SyncMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.batch.core.*;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobCompletionListenerTest {

    @Mock private SyncMetrics syncMetrics;
    @Mock private JobExecution jobExecution;
    @Mock private JobInstance jobInstance;

    private JobCompletionListener listener;

    @BeforeEach
    void setUp() {
        listener = new JobCompletionListener(syncMetrics);
        when(jobExecution.getJobInstance()).thenReturn(jobInstance);
        when(jobInstance.getJobName()).thenReturn("testJob");

        JobParameters params = new JobParametersBuilder()
                .addString("source", "source1")
                .toJobParameters();
        when(jobExecution.getJobParameters()).thenReturn(params);
    }

    @Test
    void beforeJob_shouldIncrementActiveJobs() {
        listener.beforeJob(jobExecution);
        verify(syncMetrics).incrementActiveJobs();
    }

    @Test
    void afterJob_completedStatus_shouldRecordSuccess() {
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobExecution.getId()).thenReturn(1L);
        when(jobExecution.getStartTime()).thenReturn(LocalDateTime.now().minusSeconds(5));
        when(jobExecution.getEndTime()).thenReturn(LocalDateTime.now());

        listener.afterJob(jobExecution);

        verify(syncMetrics).recordJobSuccess("testJob", "source1");
        verify(syncMetrics).decrementActiveJobs();
        verify(syncMetrics, never()).recordJobFailure(any(), any());
    }

    @Test
    void afterJob_failedStatus_shouldRecordFailure() {
        when(jobExecution.getStatus()).thenReturn(BatchStatus.FAILED);
        when(jobExecution.getId()).thenReturn(2L);
        when(jobExecution.getStartTime()).thenReturn(LocalDateTime.now().minusSeconds(2));
        when(jobExecution.getEndTime()).thenReturn(LocalDateTime.now());

        listener.afterJob(jobExecution);

        verify(syncMetrics).recordJobFailure("testJob", "source1");
        verify(syncMetrics).decrementActiveJobs();
        verify(syncMetrics, never()).recordJobSuccess(any(), any());
    }

    @Test
    void afterJob_shouldAlwaysDecrementActiveJobs() {
        when(jobExecution.getStatus()).thenReturn(BatchStatus.STOPPED);
        when(jobExecution.getId()).thenReturn(3L);
        when(jobExecution.getStartTime()).thenReturn(null);
        when(jobExecution.getEndTime()).thenReturn(null);

        listener.afterJob(jobExecution);

        verify(syncMetrics).decrementActiveJobs();
    }
}
