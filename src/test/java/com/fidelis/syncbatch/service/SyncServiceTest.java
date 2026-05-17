package com.fidelis.syncbatch.service;

import com.fidelis.syncbatch.model.SyncRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    @Mock private JobLauncher jobLauncher;
    @Mock private JobRepository jobRepository;
    @Mock private JobExplorer jobExplorer;
    @Mock private Job externalToLocalSyncJob;
    @Mock private Job localToExternalSyncJob;
    @Mock private JobExecution jobExecution;

    private SyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new SyncService(jobLauncher, jobRepository, jobExplorer, externalToLocalSyncJob, localToExternalSyncJob);
    }

    @Test
    void triggerExternalToLocal_validRequest_shouldLaunchJobAndReturnExecutionId() throws Exception {
        when(jobExecution.getId()).thenReturn(42L);
        when(jobExecution.getStatus()).thenReturn(BatchStatus.STARTED);
        when(jobLauncher.run(eq(externalToLocalSyncJob), any(JobParameters.class)))
                .thenReturn(jobExecution);

        SyncRequest request = SyncRequest.builder()
                .source("source1")
                .fullLoad(false)
                .dateFrom(LocalDateTime.now().minusDays(1))
                .build();

        long executionId = syncService.triggerExternalToLocal(request);

        assertThat(executionId).isEqualTo(42L);
        verify(jobLauncher).run(eq(externalToLocalSyncJob), any(JobParameters.class));
    }

    @Test
    void triggerLocalToExternal_validRequest_shouldLaunchJob() throws Exception {
        when(jobExecution.getId()).thenReturn(99L);
        when(jobExecution.getStatus()).thenReturn(BatchStatus.STARTED);
        when(jobLauncher.run(eq(localToExternalSyncJob), any(JobParameters.class)))
                .thenReturn(jobExecution);

        SyncRequest request = SyncRequest.builder()
                .source("source1")
                .fullLoad(true)
                .build();

        long executionId = syncService.triggerLocalToExternal(request);

        assertThat(executionId).isEqualTo(99L);
        verify(jobLauncher).run(eq(localToExternalSyncJob), any(JobParameters.class));
    }

    @Test
    void triggerExternalToLocal_missingSource_shouldThrowIllegalArgument() {
        SyncRequest request = SyncRequest.builder()
                .source("")  // blank
                .build();

        assertThatThrownBy(() -> syncService.triggerExternalToLocal(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source");
    }

    @Test
    void triggerExternalToLocal_nullSource_shouldThrow() {
        SyncRequest request = SyncRequest.builder()
                .source(null)
                .build();

        assertThatThrownBy(() -> syncService.triggerExternalToLocal(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void triggerExternalToLocal_jobLauncherThrows_shouldWrapException() throws Exception {
        when(jobLauncher.run(any(), any())).thenThrow(new RuntimeException("Batch error"));

        SyncRequest request = SyncRequest.builder().source("source1").build();

        assertThatThrownBy(() -> syncService.triggerExternalToLocal(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to launch job");
    }

    @Test
    void triggerExternalToLocal_shouldPassCorrectParams() throws Exception {
        when(jobExecution.getId()).thenReturn(1L);
        when(jobExecution.getStatus()).thenReturn(BatchStatus.STARTED);

        ArgumentCaptor<JobParameters> paramsCaptor = ArgumentCaptor.forClass(JobParameters.class);
        when(jobLauncher.run(any(), paramsCaptor.capture())).thenReturn(jobExecution);

        SyncRequest request = SyncRequest.builder()
                .source("source2")
                .fullLoad(true)
                .build();

        syncService.triggerExternalToLocal(request);

        JobParameters params = paramsCaptor.getValue();
        assertThat(params.getString("source")).isEqualTo("source2");
        assertThat(params.getString("fullLoad")).isEqualTo("true");
        assertThat(params.getLong("timestamp")).isNotNull();
    }
}
