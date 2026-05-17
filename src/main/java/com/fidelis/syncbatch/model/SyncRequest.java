package com.fidelis.syncbatch.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for triggering a sync job")
public class SyncRequest {

    @Schema(description = "External datasource key (e.g. source1, source2)", example = "source1", required = true)
    private String source;

    @Schema(description = "When true, syncs all records regardless of last_updated", example = "false")
    @Builder.Default
    private boolean fullLoad = false;

    @Schema(description = "Sync records modified after this date (ignored when fullLoad=true)", example = "2026-01-01T00:00:00")
    private LocalDateTime dateFrom;
}
