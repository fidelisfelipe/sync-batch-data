package com.fidelis.syncbatch.agent;

import com.fidelis.syncbatch.agent.model.AgentComment;
import com.fidelis.syncbatch.agent.model.SyncEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Rule-based intelligent agent that generates Portuguese comments
 * for each significant sync event.
 *
 * <p>Rules are evaluated in priority order — first match wins.
 * Future: each rule can be replaced by an LLM call for richer language.
 */
@Slf4j
@Component
public class IntelligentAgent {

    public Optional<AgentComment> analyze(SyncEvent event) {
        return switch (event.type()) {
            case JOB_STARTED       -> onJobStarted(event);
            case JOB_COMPLETED     -> onJobCompleted(event);
            case JOB_FAILED        -> onJobFailed(event);
            case STEP_COMPLETED    -> onStepCompleted(event);
            case FETCH_COMPLETED   -> onFetchCompleted(event);
            case CIRCUIT_BREAKER   -> onCircuitBreaker(event);
            case SCHEMA_INIT       -> onSchemaInit(event);
            case ERROR             -> onError(event);
            default                -> Optional.empty();
        };
    }

    // ── Rule handlers ──────────────────────────────────────────────────────────

    private Optional<AgentComment> onJobStarted(SyncEvent e) {
        String direction = e.jobName() != null && e.jobName().contains("localToExternal")
                ? "Local → " + e.source()
                : e.source() + " → Local";
        return Optional.of(AgentComment.info("🔄",
                "Iniciando sincronização " + direction,
                e.source()));
    }

    private Optional<AgentComment> onJobCompleted(SyncEvent e) {
        if (e.writeCount() == 0 && e.filterCount() == 0 && e.readCount() == 0) {
            return Optional.of(AgentComment.info("📭",
                    "Nenhum dado encontrado na fonte " + e.source() + " para sincronizar",
                    e.source()));
        }

        if (e.writeCount() == 0 && e.filterCount() > 0) {
            return Optional.of(AgentComment.info("✅",
                    String.format("Dados de %s já estão sincronizados — %d registros verificados, sem divergências",
                            e.source(), e.filterCount()),
                    e.source()));
        }

        double tps = e.throughputPerSec();
        String perfLabel = tps > 500 ? "excelente performance" :
                           tps > 100 ? "boa performance" :
                           tps > 20  ? "performance adequada" : "throughput baixo";

        String comment = String.format(
                "Sincronização %s concluída com %s — %d registros escritos em %dms",
                e.source(), perfLabel, e.writeCount(), e.durationMs());

        if (tps > 0) {
            comment += String.format(" (%.0f reg/s)", tps);
        }

        AgentComment.Severity sev = tps < 10 && e.writeCount() > 0
                ? AgentComment.Severity.WARNING
                : AgentComment.Severity.SUCCESS;

        return Optional.of(new AgentComment("✅", comment, sev, e.source(), java.time.Instant.now()));
    }

    private Optional<AgentComment> onJobFailed(SyncEvent e) {
        return Optional.of(AgentComment.error("🚨",
                "Falha na sincronização da fonte " + e.source() +
                        " — verifique conectividade e logs de erro",
                e.source()));
    }

    private Optional<AgentComment> onStepCompleted(SyncEvent e) {
        double filterRate = e.filterRate();

        if (filterRate > 80 && e.readCount() > 5) {
            return Optional.of(AgentComment.info("ℹ️",
                    String.format("Alta taxa de filtragem (%.0f%%) na fonte %s — a maior parte dos dados já estava sincronizada",
                            filterRate, e.source()),
                    e.source()));
        }

        if (e.writeCount() > 100) {
            return Optional.of(AgentComment.success("📊",
                    String.format("Volume alto processado: %d registros escritos em um único step (%s)",
                            e.writeCount(), e.jobName()),
                    e.source()));
        }

        return Optional.empty();
    }

    private Optional<AgentComment> onFetchCompleted(SyncEvent e) {
        if (e.readCount() == 0) {
            return Optional.of(AgentComment.info("📭",
                    "Nenhum registro retornado pela fonte " + e.source(),
                    e.source()));
        }
        // Only comment on large fetches to avoid noise
        if (e.readCount() > 50) {
            return Optional.of(AgentComment.info("📥",
                    String.format("Lote grande recebido da fonte %s: %d registros",
                            e.source(), e.readCount()),
                    e.source()));
        }
        return Optional.empty();
    }

    private Optional<AgentComment> onCircuitBreaker(SyncEvent e) {
        return Optional.of(AgentComment.warning("⚡",
                "Circuit breaker ativado para a fonte " + e.source() +
                        " — aguardando recuperação automática",
                e.source()));
    }

    private Optional<AgentComment> onSchemaInit(SyncEvent e) {
        return Optional.of(AgentComment.info("🗄️",
                "Schema inicializado com sucesso para a fonte externa: " + e.source(),
                e.source()));
    }

    private Optional<AgentComment> onError(SyncEvent e) {
        String msg = e.rawMessage();
        if (msg != null && msg.length() > 120) {
            msg = msg.substring(0, 120) + "...";
        }
        return Optional.of(AgentComment.error("🚨",
                "Erro detectado: " + msg,
                e.source()));
    }
}
