package com.fidelis.syncbatch.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fidelis.syncbatch.agent.model.PerformanceSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages active SSE connections and broadcasts performance snapshots.
 *
 * <p>Uses {@link CopyOnWriteArrayList} so that reads (iteration during broadcast)
 * are safe without locking, while writes (add/remove) are atomic.
 */
@Slf4j
@Component
public class MonitorBroadcaster {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.debug("SSE client disconnected — active={}", emitters.size());
        });
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.debug("SSE client timed out — active={}", emitters.size());
        });
        emitter.onError(ex -> {
            emitters.remove(emitter);
            log.debug("SSE client error: {}", ex.getMessage());
        });

        emitters.add(emitter);
        log.debug("SSE client connected — active={}", emitters.size());
        return emitter;
    }

    public void broadcast(PerformanceSnapshot snapshot) {
        if (emitters.isEmpty()) return;

        String json;
        try {
            json = mapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.warn("Failed to serialize snapshot: {}", e.getMessage());
            return;
        }

        List<SseEmitter> dead = new java.util.ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("snapshot")
                        .data(json));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }

    public int activeClients() {
        return emitters.size();
    }
}
