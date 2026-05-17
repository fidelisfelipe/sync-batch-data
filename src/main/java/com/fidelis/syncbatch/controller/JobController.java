package com.fidelis.syncbatch.controller;

import com.fidelis.syncbatch.model.SyncRequest;
import com.fidelis.syncbatch.service.SyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for triggering and monitoring sync jobs.
 *
 * <pre>
 * POST /api/jobs/sync/external-to-local   – trigger External → Local
 * POST /api/jobs/sync/local-to-external   – trigger Local → External
 * GET  /api/jobs/status/{executionId}     – poll job status
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Tag(name = "Sync Jobs", description = "Bidirectional client data synchronisation")
public class JobController {

    private final SyncService syncService;

    @PostMapping("/sync/external-to-local")
    @Operation(summary = "Trigger External → Local sync",
               description = "Launches the externalToLocalSyncJob. Returns the job execution ID.")
    public ResponseEntity<Map<String, Object>> triggerExternalToLocal(
            @RequestBody @Valid SyncRequest request) {

        log.info("REST trigger External→Local: source={} fullLoad={}",
                request.getSource(), request.isFullLoad());

        long executionId = syncService.triggerExternalToLocal(request);

        return ResponseEntity.accepted().body(Map.of(
                "message",     "Job accepted",
                "job",         "externalToLocalSyncJob",
                "source",      request.getSource(),
                "executionId", executionId
        ));
    }

    @PostMapping("/sync/local-to-external")
    @Operation(summary = "Trigger Local → External sync",
               description = "Launches the localToExternalSyncJob. Returns the job execution ID.")
    public ResponseEntity<Map<String, Object>> triggerLocalToExternal(
            @RequestBody @Valid SyncRequest request) {

        log.info("REST trigger Local→External: source={} fullLoad={}",
                request.getSource(), request.isFullLoad());

        long executionId = syncService.triggerLocalToExternal(request);

        return ResponseEntity.accepted().body(Map.of(
                "message",     "Job accepted",
                "job",         "localToExternalSyncJob",
                "source",      request.getSource(),
                "executionId", executionId
        ));
    }

    @GetMapping("/status/{executionId}")
    @Operation(summary = "Get job status", description = "Returns the status of a job execution by ID.")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable long executionId) {
        JobExecution execution = syncService.getJobExecution(executionId);

        if (execution == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
                "executionId", execution.getId(),
                "status",      execution.getStatus().name(),
                "exitCode",    execution.getExitStatus().getExitCode(),
                "startTime",   String.valueOf(execution.getStartTime()),
                "endTime",     String.valueOf(execution.getEndTime())
        ));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Simple liveness endpoint for the job controller.")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
