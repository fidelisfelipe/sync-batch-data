import React from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import type { AgentComment } from '../types';

const SEV_COLOR: Record<string, string> = {
  SUCCESS: 'var(--neon-green)',
  WARNING: 'var(--neon-yellow)',
  ERROR:   'var(--neon-red)',
  INFO:    'var(--text)',
};

function fmtTime(iso: string) {
  try { return new Date(iso).toLocaleTimeString('pt-BR'); }
  catch { return '—'; }
}

interface Props {
  comments: AgentComment[];
}

export const AgentFeed: React.FC<Props> = ({ comments }) => {
  const recent = [...comments].reverse().slice(0, 8);

  return (
    <div className="agent-panel glass">
      <div className="panel-title">Agente IA</div>
      <div className="agent-feed">
        <AnimatePresence initial={false}>
          {recent.length === 0 && (
            <motion.div key="empty" className="comment-item"
              initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
              <span className="ci-icon">🤖</span>
              <div>
                <div className="ci-text">Aguardando eventos de sincronização…</div>
                <div className="ci-time">—</div>
              </div>
            </motion.div>
          )}
          {recent.map((c) => (
            <motion.div
              key={`${c.timestamp}-${c.text.slice(0, 20)}`}
              className="comment-item"
              initial={{ x: 30, opacity: 0 }}
              animate={{ x: 0,  opacity: 1 }}
              exit={{    x: -20, opacity: 0 }}
              transition={{ type: 'spring', stiffness: 300, damping: 28 }}
            >
              <span className="ci-icon">{c.icon}</span>
              <div>
                <div className="ci-text" style={{ color: SEV_COLOR[c.severity] ?? 'var(--text)' }}>
                  {c.text}
                </div>
                <div className="ci-time">{fmtTime(c.timestamp)}</div>
              </div>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </div>
  );
};
