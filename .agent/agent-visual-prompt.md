# Etapa 4 — Dashboard High-Tech, Realtime e Animado

> **Status: ✅ CONCLUÍDA**

---

## Objetivo

Criar uma nova versão do dashboard `/monitor/live` visualmente impactante: tema cyberpunk/neon, animações em tempo real mostrando o fluxo dos dados, atualização via SSE.

---

## O que foi entregue

### Tecnologia usada

**Thymeleaf + Vanilla JS + Chart.js (CDN) + CSS animations + SVG `<animateMotion>`**

> React + TypeScript foi considerado mas descartado: exigiria build step separado, não se integra nativamente ao Thymeleaf/Spring Boot, e toda a interatividade necessária é alcançável com CSS e vanilla JS sem dependência de bundler.

---

## Arquivo entregue

`src/main/resources/templates/live-monitor.html` — template Thymeleaf puro, sem variáveis server-side (página servida diretamente em `/monitor/live`, dados chegam via SSE).

---

## Componentes visuais implementados

### Fundo animado
- Grade de pontos com drift suave (CSS `background-position` animado, 60s)
- Scanlines CRT (repeating-linear-gradient overlay)

### Header
- Título "SYNC BATCH MONITOR" com neon glow (`text-shadow` em camadas)
- Dot pulsante verde (animação `glow-pulse`) — vira amarelo em modo idle
- Indicador de conexão SSE: cinza → verde LIVE → vermelho RECONECTANDO

### 6 Cards de métricas (grid 6 colunas)
- Total Lido, Total Escrito, Filtrados, Throughput, Jobs OK, Duração Média
- Cada card tem accent bar superior colorida (neon-blue/green/purple/orange/yellow)
- Valores animados suavemente em transição numérica (`requestAnimationFrame`, easing cúbico)
- Flash de borda ao receber novo valor

### Pipeline SVG (painel principal esquerdo, 900×270 viewBox)

**Sentido External → Local (metade esquerda):**
- 2 nós de fonte (`SOURCE 1`, `SOURCE 2`) com círculo externo rotacionando (SVG `animateTransform`)
- Partículas azuis fluindo pelos paths curvos via `<animateMotion>` (3 tamanhos, offsets de tempo distintos para efeito de fluxo contínuo)
- Nó `PROCESSOR` central (retângulo com engrenagem rotacionando)
- Partículas verdes do processor para o cilindro `LOCAL DB`

**Sentido Local → External (metade direita):**
- Linha divisória tracejada com label "LOCAL → EXTERNAL"
- Partículas laranjas fluindo do LOCAL DB para 2 nós externos
- 2 nós de destino com anel rotacionando em laranja

**Barra de status inferior** (dentro do SVG):
- Preenchimento proporcional à taxa de sucesso de jobs
- Label dinâmico: "N jobs OK | N falhas | taxa: X%"

**Labels dinâmicos** no SVG: `READ: N` e `WRITE: N` atualizados a cada snapshot.

### Feed do Agente IA (painel direito)
- Lista dos últimos 7 comentários, mais recente no topo
- Animação `slide-right` ao entrar (translateX 18px → 0, opacity 0 → 1)
- Cores por severidade: SUCCESS=verde, WARNING=amarelo, ERROR=vermelho, INFO=branco
- Timestamp em hora local

### Gráfico de Throughput (Chart.js, painel inferior esquerdo)
- Tipo `line`, janela deslizante de 60 segundos
- Fill com gradiente azul → transparente
- Tension 0.45 (curva suave), sem pontos, atualização `'none'` (sem re-animação a cada tick)

### Nós de Status das Fontes (painel inferior centro)
- Um bloco por fonte presente em `sourceStatus`
- RUNNING: anel girando em azul (`animation: spin 2s linear infinite`)
- COMPLETED: anel estático verde
- FAILED: anel estático vermelho
- Labels dos nós SVG atualizados com os nomes reais das fontes

### Feed de Logs (painel inferior direito)
- Últimos 22 logs, mais recente no topo (`flex-direction: column-reverse`)
- Linhas com `🔴` → classe `.err` (vermelho), `🟡` → classe `.wrn` (amarelo)
- Fade-in ao entrar

---

## Conexão SSE

```js
const es = new EventSource('/monitor/stream');
es.addEventListener('snapshot', e => applySnapshot(JSON.parse(e.data)));
es.onopen  = () => { dot.className = 'live'; }
es.onerror = () => {
    es.close();
    fetch('/monitor/status').then(r => r.json()).then(applySnapshot); // fallback poll
    setTimeout(connect, 3000);
}
```

Fallback de polling via `GET /monitor/status` é disparado imediatamente a cada erro de SSE antes da reconexão.

---

## Cache de Thymeleaf

Em desenvolvimento (`application-dev.yml`), `spring.thymeleaf.cache: false` garante que edições no template são refletidas sem restart.

---

## O que foi simplificado em relação ao plano original

| Plano original | O que foi feito | Motivo |
|---|---|---|
| React + TypeScript + Recharts + Framer Motion | Thymeleaf + vanilla JS + Chart.js + CSS/SVG | Sem build step; integração nativa com Spring Boot |
| Heatmap de performance por tabela/fonte | Não implementado | Requer dados por tabela (disponíveis na Etapa 2 de modelagem) |
| Gauge animado para taxa de sucesso | Barra de progresso no SVG | Visualmente equivalente, mais fácil de posicionar no layout |
| Panels Grafana atualizados | Grafana existente mantido | O dashboard live SSE substitui o Grafana para visualização em tempo real |
| Histórico de execuções (timeline) | Não implementado | Requer persistência de `SyncExecution` (Etapa 2) |

---

## Paleta de cores (CSS custom properties)

```css
--bg:           #05090d    /* fundo principal */
--surface:      rgba(8,18,30,0.88)   /* glass cards */
--neon-blue:    #00c8ff    /* métricas read, conexão SSE, pipeline ext→local */
--neon-green:   #00ff99    /* métricas write, jobs OK, LOCAL DB, COMPLETED */
--neon-purple:  #b060ff    /* filtrados, processor */
--neon-orange:  #ff7733    /* throughput, pipeline local→ext */
--neon-red:     #ff2255    /* erros, FAILED */
--neon-yellow:  #ffcc00    /* duração, WARNING, idle */
```
