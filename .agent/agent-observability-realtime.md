# Etapa 3 — Agente Inteligente de Monitoramento em Tempo Real

> **Status: ✅ CONCLUÍDA**

---

## Objetivo

Adicionar ao projeto `sync-batch-data` um **Agente Inteligente** que leia os logs da aplicação em tempo real, analise a performance das sincronizações e exiba comentários inteligentes em um dashboard web ao vivo.

---

## Arquitetura implementada

```
Logback → LogEventAppender (AppenderBase<ILoggingEvent>)
               ↓  LinkedBlockingQueue<8000> (buffer estático)
          PerformanceAnalyzer (@Scheduled fixedDelay=1000ms)
               ↓  drena até 500 eventos/tick via regex
          SyncEvent (record com EventType enum)
               ↓
          IntelligentAgent (12 regras por tipo de evento)
               ↓  Optional<AgentComment>
          MonitorBroadcaster (SSE via CopyOnWriteArrayList<SseEmitter>)
               ↓
          Browser (EventSource API, auto-reconnect em 3s)
```

> **SSE foi escolhido sobre WebSocket**: menor overhead de configuração (sem `WebSocketConfig`), suporte nativo em todos os browsers modernos, e o fluxo é unidirecional (servidor → cliente) — WebSocket seria over-engineering aqui.

> **Logback AppenderBase foi escolhido sobre tail de arquivo de log**: elimina I/O de disco, desacoplado via fila estática (Logback inicializa antes do contexto Spring, portanto a fila precisa ser `static`).

---

## Estrutura de pacotes criada

```
com.fidelis.syncbatch/
└── agent/
    ├── LogEventAppender.java       (Logback AppenderBase — canal desacoplado)
    ├── PerformanceAnalyzer.java    (@Scheduled, parser regex, métricas acumuladas)
    ├── IntelligentAgent.java       (12 regras, comentários em português)
    ├── MonitorBroadcaster.java     (SSE — CopyOnWriteArrayList<SseEmitter>)
    └── model/
        ├── SyncEvent.java          (record: EventType, job, source, counts, duration)
        ├── AgentComment.java       (record: icon, text, Severity, context, timestamp)
        └── PerformanceSnapshot.java (@Data @Builder — estado completo para serialização SSE)

controller/
└── LiveMonitorController.java
    ├── GET /monitor/live   → Thymeleaf "live-monitor"
    ├── GET /monitor/stream → SSE (TEXT_EVENT_STREAM_VALUE)
    └── GET /monitor/status → JSON snapshot (polling fallback)
```

---

## Coleta de logs

`LogEventAppender` estende `AppenderBase<ILoggingEvent>` do Logback. Configurado em `logback-spring.xml` apenas para loggers `com.fidelis.syncbatch`, com nível DEBUG. A fila `LinkedBlockingQueue<8000>` é estática para sobreviver ao ciclo de vida do contexto Logback.

---

## Eventos reconhecidos (regex patterns)

| Pattern | EventType | O que dispara |
|---|---|---|
| `Job starting: job=X source=Y` | `JOB_STARTED` | Início de qualquer job |
| `Job (COMPLETED\|FAILED): job=X source=Y durationMs=N` | `JOB_COMPLETED` / `JOB_FAILED` | Fim de job com duração |
| `Step completed: step=X read=N write=N skip=N filter=N status=S` | `STEP_COMPLETED` | Fim de step com contadores |
| `Fetched N clients from source=X` | `FETCH_COMPLETED` | Leitura de batch externo |
| `ExternalClientWriter: wrote N items to source=X` | `WRITE_COMPLETED` | Escrita em fonte externa |
| `Circuit breaker open for source=X` | `CIRCUIT_BREAKER` | CB aberto |
| `Schema initialised for external source: X` | `SCHEMA_INIT` | Schema criado/verificado |
| Nível ERROR (exceto ruído HTTP) | `ERROR` | Qualquer erro da aplicação |

**Enriquecimento JOB_COMPLETED**: o log de job não contém read/write/filter. O `PerformanceAnalyzer` cacheia os dados do `STEP_COMPLETED` imediatamente anterior em `lastStepData: Map<String, long[]>` (chave = prefixo do tipo de job) e injeta esses dados no evento `JOB_COMPLETED`.

---

## Regras do IntelligentAgent (comentários em português)

| EventType | Condição | Severidade | Comentário gerado |
|---|---|---|---|
| `JOB_STARTED` | — | INFO | "Iniciando sincronização {direção}" |
| `JOB_COMPLETED` | read=0, write=0, filter=0 | INFO | "Nenhum dado encontrado na fonte X" |
| `JOB_COMPLETED` | write=0, filter>0 | INFO | "Dados de X já sincronizados — N registros verificados" |
| `JOB_COMPLETED` | write>0, tps>500 | SUCCESS | "…com excelente performance — N registros em Xms (Y reg/s)" |
| `JOB_COMPLETED` | write>0, tps<10 | WARNING | "…throughput baixo" |
| `JOB_FAILED` | — | ERROR | "Falha na sincronização da fonte X — verifique conectividade" |
| `STEP_COMPLETED` | filterRate>80% e read>5 | INFO | "Alta taxa de filtragem (N%) — maior parte já sincronizada" |
| `STEP_COMPLETED` | write>100 | SUCCESS | "Volume alto processado: N registros num único step" |
| `FETCH_COMPLETED` | read=0 | INFO | "Nenhum registro retornado pela fonte X" |
| `FETCH_COMPLETED` | read>50 | INFO | "Lote grande recebido da fonte X: N registros" |
| `CIRCUIT_BREAKER` | — | WARNING | "Circuit breaker ativado para X — aguardando recuperação" |
| `SCHEMA_INIT` | — | INFO | "Schema inicializado com sucesso para X" |
| `ERROR` | — | ERROR | "Erro detectado: {mensagem truncada em 120 chars}" |

---

## Métricas acumuladas (PerformanceAnalyzer)

- `AtomicLong`: totalRead, totalWritten, totalFiltered, totalSkipped, jobsCompleted, jobsFailed
- `Deque<Long> recentDurations` — janela deslizante de 30 durações para cálculo de média
- `Map<String, String> sourceStatus` — status corrente de cada fonte (RUNNING/COMPLETED/FAILED)
- Ring buffers: `recentComments` (50 entradas), `recentLogs` (100 entradas)
- Detecção de idle: sem eventos por 60 segundos

---

## Filtros anti-ruído

Logs ruidosos excluídos do feed de UI e da análise de erros:
- `favicon.ico`, `No static resource`, `Broken pipe`, `Connection reset`
- Logger `GlobalExceptionHandler` (gera erros HTTP irrelevantes para o negócio)

---

## Configuração (logback-spring.xml)

```xml
<appender name="SYNC_AGENT" class="com.fidelis.syncbatch.agent.LogEventAppender"/>

<logger name="com.fidelis.syncbatch" level="DEBUG" additivity="true">
    <appender-ref ref="SYNC_AGENT"/>
</logger>

<springProfile name="dev">
    <logger name="com.fidelis.syncbatch" level="DEBUG" additivity="false">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="SYNC_AGENT"/>
    </logger>
</springProfile>
```

---

## O que foi simplificado em relação ao plano original

| Plano original | O que foi feito | Motivo |
|---|---|---|
| WebSocket | SSE | Unidirecional, sem config adicional |
| Tail de arquivo de log | Logback AppenderBase | Sem I/O, sem risco de rotação de arquivo |
| `LogTailService.java` | `LogEventAppender.java` | Nomenclatura reflete a implementação real |
| `dto/` package | `model/` package dentro de `agent/` | Sem DTOs separados — records usados diretamente |
| `PerformanceWebSocketHandler` + `WebSocketConfig` | `MonitorBroadcaster` | SSE não precisa de handler de WS |
| Persistir AgentComment em banco | Ring buffer em memória | Suficiente para o dashboard; persitência pode ser adicionada na Etapa 2 |

---

## Bugs corrigidos durante a execução desta etapa

| Bug | Causa | Correção |
|---|---|---|
| Comentários "Nenhum dado" para jobs que escreveram dados | `JOB_COMPLETED` log não contém contadores — agent recebia evento com counts=0 | Cache `lastStepData` no `PerformanceAnalyzer` enriquece o evento de JOB_COMPLETED com dados do STEP anterior |
| Erros HTTP aparecem como "Erro detectado" no feed | `GlobalExceptionHandler` loga HTTP 404/500 em nível ERROR | Filtros adicionados em `isRelevant()` e na branch ERROR de `parseEvent()` |
