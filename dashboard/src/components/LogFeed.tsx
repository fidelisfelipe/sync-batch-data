import React from 'react';
import { AnimatePresence, motion } from 'framer-motion';

interface Props {
  logs: string[];
}

export const LogFeed: React.FC<Props> = ({ logs }) => {
  const recent = [...logs].reverse().slice(0, 24);

  return (
    <div className="logs-panel glass">
      <div className="panel-title">Log Feed</div>
      <div className="logs-feed">
        <AnimatePresence initial={false}>
          {recent.map((line, i) => {
            const cls = line.startsWith('🔴') ? 'err' : line.startsWith('🟡') ? 'wrn' : '';
            return (
              <motion.div
                key={`${i}-${line.slice(0, 30)}`}
                className={`log-line ${cls}`}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ duration: 0.2 }}
              >
                {line}
              </motion.div>
            );
          })}
        </AnimatePresence>
      </div>
    </div>
  );
};
