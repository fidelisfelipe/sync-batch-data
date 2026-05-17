package com.fidelis.syncbatch.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fidelis.syncbatch.model.SyncRequest;
import com.fidelis.syncbatch.service.SyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.fidelis.syncbatch.controller.GlobalExceptionHandler;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {JobController.class, GlobalExceptionHandler.class})
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SyncService syncService;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void triggerExternalToLocal_validRequest_shouldReturn202() throws Exception {
        when(syncService.triggerExternalToLocal(any(SyncRequest.class))).thenReturn(42L);

        SyncRequest request = SyncRequest.builder()
                .source("source1")
                .fullLoad(false)
                .build();

        mockMvc.perform(post("/api/jobs/sync/external-to-local")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.executionId").value(42))
                .andExpect(jsonPath("$.job").value("externalToLocalSyncJob"))
                .andExpect(jsonPath("$.source").value("source1"));
    }

    @Test
    void triggerLocalToExternal_validRequest_shouldReturn202() throws Exception {
        when(syncService.triggerLocalToExternal(any(SyncRequest.class))).thenReturn(99L);

        SyncRequest request = SyncRequest.builder()
                .source("source1")
                .fullLoad(true)
                .build();

        mockMvc.perform(post("/api/jobs/sync/local-to-external")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.executionId").value(99))
                .andExpect(jsonPath("$.job").value("localToExternalSyncJob"));
    }

    @Test
    void health_shouldReturn200WithStatusUp() throws Exception {
        mockMvc.perform(get("/api/jobs/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void triggerExternalToLocal_serviceThrows_shouldReturn500() throws Exception {
        when(syncService.triggerExternalToLocal(any()))
                .thenThrow(new RuntimeException("Job failed"));

        SyncRequest request = SyncRequest.builder().source("source1").build();

        mockMvc.perform(post("/api/jobs/sync/external-to-local")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void getStatus_notFound_shouldReturn404() throws Exception {
        when(syncService.getJobExecution(999L)).thenReturn(null);

        mockMvc.perform(get("/api/jobs/status/999"))
                .andExpect(status().isNotFound());
    }
}
