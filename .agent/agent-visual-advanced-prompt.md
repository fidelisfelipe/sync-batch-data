# Prompt - Dashboard High-Tech Realtime com Animações Baseadas em Eventos Reais

Crie uma **nova versão avançada do dashboard** para o projeto `sync-batch-data`, com forte ênfase em **animações reais e data-driven**, ou seja, as animações devem refletir exatamente o que está acontecendo na aplicação em tempo real.

### Requisitos Principais:

**1. Animações Conectadas aos Eventos Reais**
- As animações devem ser disparadas por eventos reais da aplicação (não fictícios).
- Quando um Job Spring Batch inicia → animação de "início de fluxo" deve aparecer.
- Quando registros são lidos do fonte externo → partículas ou bolinhas devem se mover do nó "SOURCE" para o "PROCESSOR".
- Quando o Processor está trabalhando → animação de processamento/conflict resolve deve ficar ativa.
- Quando registros são gravados no banco local → fluxo deve continuar até o nó "LOCAL DB".
- Taxa de throughput, contadores e status devem atualizar em tempo real.

**2. Elementos Visuais Dinâmicos e High-Tech**

- **Pipeline Central Animado** (o mais importante):
    - Nós: External Sources → Processor → Local Database
    - Fluxo de dados representado por partículas, linhas ou bolinhas que se movem conforme o progresso real do batch.
    - Velocidade do fluxo deve variar conforme o throughput atual.

- **Cards com Contadores Animados**:
    - Total Lido, Total Escrito, Throughput, Duração Média, etc.
    - Os números devem subir suavemente (count-up animation) quando novos dados chegam.

- **Mapa de Fontes**:
    - Cada fonte externa como um nó.
    - Linhas ou setas que acendem e fluem quando aquela fonte está sincronizando.

- **Feed do Agente IA** (lado direito):
    - Comentários inteligentes aparecendo em tempo real com animação de entrada.

**3. Tecnologias Recomendadas**
- **Opção Principal (Recomendada)**: React + TypeScript + Vite + Recharts / Tremor + Framer Motion ou React Spring para animações.
- Comunicação via **WebSocket** (STOMP ou puro) ou Server-Sent Events (SSE).
- Backend deve expor um endpoint WebSocket com eventos em tempo real (JobStarted, RecordsRead, StepProgress, JobCompleted, etc.).

### Painéis Obrigatórios

1. **Header** com título "SYNC BATCH MONITOR" + status LIVE
2. **Row de Métricas** (6 cards com animações)
3. **Fluxo de Dados em Tempo Real** (área central grande)
4. **Status das Fontes Externas**
5. **Agente IA - Comentários Inteligentes** (live feed)
6. **Gráficos de Performance**

**Estilo Visual**: Futurista, dark theme, neon accents (azul, ciano, roxo, verde), efeitos de glow e partículas sutis.

**Importante**:
- Todas as animações devem ser **event-driven** (disparadas por mensagens do backend).
- O dashboard deve funcionar mesmo se não houver atividade (mostrando estado idle).

Gere o código completo da aplicação frontend (React) + as classes necessárias no backend Spring Boot para enviar os eventos em tempo real via WebSocket.

Comece agora.