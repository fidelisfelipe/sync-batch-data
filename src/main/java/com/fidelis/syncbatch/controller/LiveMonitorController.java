package com.fidelis.syncbatch.controller;

import com.fidelis.syncbatch.agent.MonitorBroadcaster;
import com.fidelis.syncbatch.agent.PerformanceAnalyzer;
import com.fidelis.syncbatch.agent.model.PerformanceSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Serves the live monitoring dashboard and its data endpoints.
 *
 * <pre>
 * GET /monitor/live   → Thymeleaf dashboard page
 * GET /monitor/stream → SSE stream (snapshot every ~1 s)
 * GET /monitor/status → current snapshot as JSON (polling fallback)
 * </pre>
 */
@Controller
@RequestMapping("/monitor")
@RequiredArgsConstructor
@Tag(name = "Monitor", description = "Live observability dashboard")
public class LiveMonitorController {

    private final MonitorBroadcaster broadcaster;
    private final PerformanceAnalyzer analyzer;

    @GetMapping("/live")
    public String liveDashboard() {
        return "live-monitor";
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    @Operation(summary = "SSE stream of performance snapshots (~1 s interval)")
    public SseEmitter stream() {
        return broadcaster.subscribe();
    }

    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Operation(summary = "Current performance snapshot (polling fallback)")
    public PerformanceSnapshot status() {
        return analyzer.getSnapshot();
    }
}
