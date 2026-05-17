import React from 'react';
import { motion } from 'framer-motion';

const STATUS_COLOR: Record<string, string> = {
  RUNNING:   'var(--neon-blue)',
  COMPLETED: 'var(--neon-green)',
  FAILED:    'var(--neon-red)',
};

interface Props {
  sourceStatus: Record<string, string>;
}

export const SourceMap: React.FC<Props> = ({ sourceStatus }) => {
  const entries = Object.entries(sourceStatus);

  return (
    <div className="sources-panel glass">
      <div className="panel-title">Fontes Externas</div>
      <div className="sources-list">
        {entries.length === 0 && (
          <div className="src-node">
            <div className="src-ring"><div className="src-dot"/><div className="src-ring-outer"/></div>
            <div className="src-info">
              <div className="src-name" style={{ color: 'var(--text-dim)' }}>Aguardando…</div>
              <div className="src-status-txt">nenhuma fonte ativa</div>
            </div>
          </div>
        )}
        {entries.map(([name, status]) => {
          const color = STATUS_COLOR[status] ?? 'var(--text-dim)';
          return (
            <motion.div
              key={name}
              className={`src-node ${status}`}
              layout
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.3 }}
            >
              <div className="src-ring">
                <div className="src-dot"/>
                <motion.div
                  className="src-ring-outer"
                  animate={status === 'RUNNING' ? { rotate: 360 } : {}}
                  transition={status === 'RUNNING' ? { duration: 2, repeat: Infinity, ease: 'linear' } : {}}
                />
              </div>
              <div className="src-info">
                <div className="src-name" style={{ color }}>{name.toUpperCase()}</div>
                <div className="src-status-txt">{status}</div>
              </div>
              {status === 'RUNNING' && (
                <motion.div
                  style={{
                    width: 8, height: 8, borderRadius: '50%',
                    background: 'var(--neon-blue)',
                    boxShadow: '0 0 8px var(--neon-blue)',
                    flexShrink: 0,
                  }}
                  animate={{ opacity: [1, 0.2, 1] }}
                  transition={{ duration: 1, repeat: Infinity }}
                />
              )}
            </motion.div>
          );
        })}
      </div>
    </div>
  );
};
