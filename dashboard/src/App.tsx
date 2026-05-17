import './App.css';
import { motion } from 'framer-motion';
import { useSyncStream } from './hooks/useSyncStream';
import { AgentFeed } from './components/AgentFeed';
import { LogFeed } from './components/LogFeed';
import { MetricCards } from './components/MetricCards';
import { Pipeline } from './components/Pipeline';
import { SourceMap } from './components/SourceMap';
import { ThroughputChart } from './components/ThroughputChart';

function timeAgo(iso: string | undefined) {
  if (!iso) return '—';
  const s = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 5)  return 'agora';
  if (s < 60) return `${s}s atrás`;
  const m = Math.floor(s / 60);
  return m < 60 ? `${m}m atrás` : `${Math.floor(m / 60)}h atrás`;
}

export default function App() {
  const { snapshot, pipelineState, activeSource, activeDirection, connected } = useSyncStream();

  const snap = snapshot ?? {
    totalRead: 0, totalWritten: 0, totalFiltered: 0, totalSkipped: 0,
    jobsCompleted: 0, jobsFailed: 0, throughputPerSec: 0,
    avgDurationMs: 0, lastDurationMs: 0,
    sourceStatus: {}, lastActivity: '', idle: true,
    recentComments: [], recentLogs: [],
  };

  const sources = Object.keys(snap.sourceStatus);

  return (
    <div className="app">
      {/* ── Header ── */}
      <header className="glass header">
        <div className="header-left">
          <motion.div
            className="logo-pulse"
            animate={{
              boxShadow: snap.idle
                ? ['0 0 6px #ffcc00', '0 0 18px #ffcc00', '0 0 6px #ffcc00']
                : ['0 0 6px #00ff99', '0 0 22px #00ff99', '0 0 6px #00ff99'],
              background: snap.idle ? '#ffcc00' : '#00ff99',
            }}
            transition={{ duration: 2, repeat: Infinity }}
          />
          <h1>Sync Batch Monitor</h1>
          {pipelineState === 'running' && activeSource && (
            <motion.span
              initial={{ opacity: 0, x: -10 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0 }}
              style={{
                fontSize: '0.7rem', letterSpacing: '2px',
                color: 'var(--neon-blue)', border: '1px solid var(--neon-blue)',
                padding: '2px 10px', borderRadius: '20px',
                boxShadow: '0 0 8px rgba(0,200,255,0.3)',
              }}
            >
              {activeSource.toUpperCase()} · {activeDirection === 'local-to-external' ? 'LOCAL → EXT' : 'EXT → LOCAL'}
            </motion.span>
          )}
        </div>
        <div className="header-right">
          <div className="conn-wrap">
            <div className={`conn-dot ${connected ? 'live' : 'error'}`}/>
            <span>{connected ? 'LIVE' : 'RECONECTANDO…'}</span>
          </div>
          <span>última atividade: {timeAgo(snap.lastActivity)}</span>
        </div>
      </header>

      {/* ── Metric cards ── */}
      <MetricCards
        totalRead={snap.totalRead}
        totalWritten={snap.totalWritten}
        totalFiltered={snap.totalFiltered}
        throughput={snap.throughputPerSec}
        jobsCompleted={snap.jobsCompleted}
        jobsFailed={snap.jobsFailed}
        avgDurationMs={snap.avgDurationMs}
      />

      {/* ── Pipeline + Agent ── */}
      <div className="main-row">
        <div className="glass pipeline-panel">
          <div className="panel-title">Fluxo de Dados em Tempo Real</div>
          <div className="pipeline-wrap">
            <Pipeline
              state={pipelineState}
              direction={activeDirection}
              sources={sources}
              throughput={snap.throughputPerSec}
              totalRead={snap.totalRead}
              totalWritten={snap.totalWritten}
              jobsCompleted={snap.jobsCompleted}
              jobsFailed={snap.jobsFailed}
            />
          </div>
        </div>
        <AgentFeed comments={snap.recentComments}/>
      </div>

      {/* ── Bottom row ── */}
      <div className="bottom-row">
        <ThroughputChart throughput={snap.throughputPerSec}/>
        <SourceMap sourceStatus={snap.sourceStatus}/>
        <LogFeed logs={snap.recentLogs}/>
      </div>
    </div>
  );
}
