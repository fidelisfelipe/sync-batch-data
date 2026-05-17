import React, { CSSProperties, useEffect, useRef, useState } from 'react';
import { motion } from 'framer-motion';

// Smooth counter that animates from previous value to new target
function useCounter(target: number, duration = 650) {
  const [value, setValue] = useState(target);
  const fromRef = useRef(target);

  useEffect(() => {
    const from = fromRef.current;
    if (from === target) return;
    const start = performance.now();
    let raf: number;
    const step = (now: number) => {
      const t = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - t, 3); // cubic ease-out
      setValue(Math.round(from + (target - from) * eased));
      if (t < 1) { raf = requestAnimationFrame(step); }
      else { setValue(target); fromRef.current = target; }
    };
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [target, duration]);

  return value;
}

interface CardProps {
  label: string;
  value: number;
  sub?: string;
  icon: string;
  accent: string;
  suffix?: string;
  decimals?: number;
  updated?: boolean;
}

const MetricCard: React.FC<CardProps> = ({ label, value, sub, icon, accent, suffix = '', decimals = 0, updated }) => {
  const display = useCounter(decimals === 0 ? Math.round(value) : value);
  const formatted = decimals > 0 ? display.toFixed(decimals) : display.toLocaleString('pt-BR');

  return (
    <motion.div
      className="card"
      style={{ '--accent': accent } as CSSProperties}
      animate={updated ? { borderColor: accent, boxShadow: `0 0 18px ${accent}33` } : {}}
      transition={{ duration: 0.3 }}
    >
      <span className="card-icon">{icon}</span>
      <div className="card-label">{label}</div>
      <motion.div
        className="card-value"
        animate={updated ? { scale: [1, 1.05, 1] } : {}}
        transition={{ duration: 0.4 }}
        style={{ color: accent, textShadow: `0 0 14px ${accent}` }}
      >
        {formatted}{suffix}
      </motion.div>
      {sub && <div className="card-sub">{sub}</div>}
    </motion.div>
  );
};

interface Props {
  totalRead: number;
  totalWritten: number;
  totalFiltered: number;
  throughput: number;
  jobsCompleted: number;
  jobsFailed: number;
  avgDurationMs: number;
}

export const MetricCards: React.FC<Props> = ({
  totalRead, totalWritten, totalFiltered, throughput, jobsCompleted, jobsFailed, avgDurationMs,
}) => {
  const prevRef = useRef({ totalRead, totalWritten, throughput, jobsCompleted });
  const updated = {
    read:    totalRead    !== prevRef.current.totalRead,
    written: totalWritten !== prevRef.current.totalWritten,
    tps:     throughput   !== prevRef.current.throughput,
    jobs:    jobsCompleted !== prevRef.current.jobsCompleted,
  };
  useEffect(() => { prevRef.current = { totalRead, totalWritten, throughput, jobsCompleted }; });

  return (
    <div className="cards-row">
      <MetricCard label="Total Lido"     value={totalRead}     icon="📥" accent="var(--neon-blue)"   sub="registros lidos"    updated={updated.read}/>
      <MetricCard label="Total Escrito"  value={totalWritten}  icon="📤" accent="var(--neon-green)"  sub="registros gravados" updated={updated.written}/>
      <MetricCard label="Filtrados"      value={totalFiltered} icon="🔍" accent="var(--neon-purple)" sub="já sincronizados"/>
      <MetricCard label="Throughput"     value={Math.round(throughput)} icon="⚡" accent="var(--neon-orange)" sub="registros/segundo" updated={updated.tps}/>
      <MetricCard label="Jobs OK"        value={jobsCompleted} icon="✅" accent="var(--neon-green)"  sub={`falhas: ${jobsFailed}`} updated={updated.jobs}/>
      <MetricCard label="Duração Média"  value={avgDurationMs} icon="⏱" accent="var(--neon-yellow)" sub="ms por job"/>
    </div>
  );
};
