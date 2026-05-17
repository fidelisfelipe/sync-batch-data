import React from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import type { PipelineState, SyncDirection } from '../types';

// ── SVG paths (animateMotion path strings) ────────────────────────────────
const P_S1_PROC = 'M106,75 Q220,75 220,110';
const P_S2_PROC = 'M106,198 Q220,198 220,110';
const P_PROC_DB = 'M276,110 Q380,115 380,124';
const P_DB_E1   = 'M426,124 Q600,75 788,75';
const P_DB_E2   = 'M426,150 Q600,200 788,198';

// ── Particle component ────────────────────────────────────────────────────
interface ParticleProps {
  path: string;
  dur: string;
  delay: string;
  r?: number;
  fill: string;
  opacity?: number;
  filterId?: string;
}
const Particle: React.FC<ParticleProps> = ({
  path, dur, delay, r = 4, fill, opacity = 0.9, filterId,
}) => (
  <circle r={r} fill={fill} opacity={opacity} filter={filterId ? `url(#${filterId})` : undefined}>
    <animateMotion dur={dur} begin={delay} repeatCount="indefinite" path={path} />
  </circle>
);

// ── Props ─────────────────────────────────────────────────────────────────
interface Props {
  state: PipelineState;
  direction: SyncDirection | null;
  sources: string[];
  throughput: number;
  totalRead: number;
  totalWritten: number;
  jobsCompleted: number;
  jobsFailed: number;
}

export const Pipeline: React.FC<Props> = ({
  state, direction, sources, throughput,
  totalRead, totalWritten, jobsCompleted, jobsFailed,
}) => {
  const isRunning   = state === 'running';
  const isCompleted = state === 'completed';
  const isFailed    = state === 'failed';

  // Particle speed: higher throughput → shorter duration → faster particles
  const tps      = Math.max(throughput, 0);
  const rawDur   = Math.max(0.55, 3.6 - tps / 160);
  const pDur     = `${rawDur.toFixed(2)}s`;
  const pDurFast = `${(rawDur * 0.6).toFixed(2)}s`;
  const pDurSlow = `${(rawDur * 1.3).toFixed(2)}s`;

  const showEtL = isRunning && (direction === 'external-to-local' || direction === null);
  const showLtE = isRunning && (direction === 'local-to-external' || direction === null);

  // Dynamic node colors
  const srcStroke = isRunning ? '#00c8ff' : isCompleted ? '#00ff99' : isFailed ? '#ff2255' : 'rgba(0,200,255,0.35)';
  const srcFillA  = isRunning ? 0.12 : 0.04;
  const procStroke = isRunning ? '#b060ff' : 'rgba(176,96,255,0.35)';
  const dbStroke   = isRunning || isCompleted ? '#00ff99' : 'rgba(0,255,153,0.35)';
  const extStroke  = isRunning ? '#ff7733' : 'rgba(255,119,51,0.35)';

  const src1 = (sources[0] ?? 'SRC 1').toUpperCase().slice(0, 7);
  const src2 = (sources[1] ?? 'SRC 2').toUpperCase().slice(0, 7);

  const total      = jobsCompleted + jobsFailed;
  const successPct = total > 0 ? ((jobsCompleted / total) * 100).toFixed(0) : null;

  return (
    <svg viewBox="0 0 900 280" style={{ width: '100%', height: '100%' }}>
      <defs>
        <filter id="glow-b">
          <feGaussianBlur stdDeviation="3.5" result="b"/>
          <feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
        </filter>
        <filter id="glow-s">
          <feGaussianBlur stdDeviation="2" result="b"/>
          <feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
        </filter>
        <linearGradient id="gr-bl" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%"   stopColor="#00c8ff" stopOpacity="0.8"/>
          <stop offset="100%" stopColor="#b060ff" stopOpacity="0.8"/>
        </linearGradient>
        <linearGradient id="gr-gr" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%"   stopColor="#b060ff" stopOpacity="0.8"/>
          <stop offset="100%" stopColor="#00ff99" stopOpacity="0.8"/>
        </linearGradient>
        <linearGradient id="gr-or" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%"   stopColor="#00ff99" stopOpacity="0.6"/>
          <stop offset="100%" stopColor="#ff7733" stopOpacity="0.8"/>
        </linearGradient>
      </defs>

      {/* ═══ Source nodes ═══ */}
      {[{ cy: 75, label: src1, dir: '0,360' }, { cy: 198, label: src2, dir: '360,0' }].map(({ cy, label, dir }) => (
        <g key={cy} transform={`translate(70,${cy})`}>
          <circle r={36} fill={`rgba(0,200,255,${srcFillA})`} stroke={srcStroke} strokeWidth={1.5}/>
          <circle r={22} fill={`rgba(0,200,255,${srcFillA * 2})`}/>
          <text y={5} textAnchor="middle" fill={srcStroke} fontSize={9} fontFamily="monospace" fontWeight="bold">{label}</text>
          <circle r={36} fill="none" stroke={srcStroke} strokeWidth={1} strokeDasharray="9 5" opacity={0.4}>
            <animateTransform attributeName="transform" type="rotate"
              from={dir.split(',')[0]} to={dir.split(',')[1]}
              dur={isRunning ? '5s' : '14s'} repeatCount="indefinite"/>
          </circle>
        </g>
      ))}

      {/* ═══ Paths: src → processor ═══ */}
      <path d={P_S1_PROC} fill="none" stroke="url(#gr-bl)" strokeWidth={1.5} opacity={isRunning ? 0.6 : 0.22}/>
      <path d={P_S2_PROC} fill="none" stroke="url(#gr-bl)" strokeWidth={1.5} opacity={isRunning ? 0.6 : 0.22}/>

      {/* ═══ Processor node ═══ */}
      <g transform="translate(220,85)">
        <rect x={-56} y={-28} width={112} height={58} rx={8}
          fill={`rgba(176,96,255,${isRunning ? 0.15 : 0.06})`}
          stroke={procStroke} strokeWidth={1.5}/>
        <text y={-9} textAnchor="middle" fill={procStroke}
          fontSize={8.5} fontFamily="monospace" fontWeight="bold">PROCESSOR</text>
        <text y={7} textAnchor="middle" fill={procStroke}
          fontSize={7} fontFamily="monospace" opacity={0.75}>Conflict Resolve</text>
        <g transform="translate(0,22)">
          <circle r={7} fill="none" stroke={procStroke} strokeWidth={1.2} strokeDasharray="4 2">
            <animateTransform attributeName="transform" type="rotate"
              from="0" to="360" dur={isRunning ? '1.4s' : '4s'} repeatCount="indefinite"/>
          </circle>
          <circle r={3} fill={`rgba(176,96,255,${isRunning ? 0.5 : 0.2})`}/>
        </g>
      </g>

      {/* ═══ Path: processor → Local DB ═══ */}
      <path d={P_PROC_DB} fill="none" stroke="url(#gr-gr)" strokeWidth={1.5} opacity={isRunning ? 0.6 : 0.22}/>

      {/* ═══ Local DB node ═══ */}
      <g transform="translate(380,137)">
        <ellipse cx={0} cy={-18} rx={46} ry={13}
          fill={`rgba(0,255,153,${isRunning || isCompleted ? 0.14 : 0.06})`}
          stroke={dbStroke} strokeWidth={1.5}/>
        <rect x={-46} y={-18} width={92} height={22} fill={`rgba(0,255,153,${isRunning ? 0.07 : 0.03})`}/>
        <line x1={-46} y1={-18} x2={-46} y2={4} stroke={dbStroke} strokeWidth={1.5}/>
        <line x1={46}  y1={-18} x2={46}  y2={4} stroke={dbStroke} strokeWidth={1.5}/>
        <ellipse cx={0} cy={4} rx={46} ry={13}
          fill={`rgba(0,255,153,${isRunning ? 0.08 : 0.04})`}
          stroke={dbStroke} strokeWidth={1.5}/>
        <text y={-12} textAnchor="middle" fill={dbStroke}
          fontSize={8.5} fontFamily="monospace" fontWeight="bold">LOCAL DB</text>
        <text y={3} textAnchor="middle" fill="rgba(0,255,153,0.45)"
          fontSize={7} fontFamily="monospace">H2 / PostgreSQL</text>
      </g>

      {/* ═══ Paths: Local DB → external targets ═══ */}
      <path d={P_DB_E1} fill="none" stroke="url(#gr-or)" strokeWidth={1.5} opacity={isRunning ? 0.5 : 0.18}/>
      <path d={P_DB_E2} fill="none" stroke="url(#gr-or)" strokeWidth={1.5} opacity={isRunning ? 0.5 : 0.18}/>

      {/* ═══ Divider ═══ */}
      <line x1={480} y1={15} x2={480} y2={255} stroke="rgba(0,200,255,0.08)" strokeWidth={1} strokeDasharray="5 4"/>
      <text x={486} y={26} fill="rgba(0,200,255,0.28)" fontSize={7} fontFamily="monospace">LOCAL → EXTERNAL</text>

      {/* ═══ External target nodes ═══ */}
      {[{ cy: 75 }, { cy: 198 }].map(({ cy }, i) => (
        <g key={cy} transform={`translate(818,${cy})`}>
          <circle r={30} fill={`rgba(255,119,51,${isRunning ? 0.12 : 0.04})`} stroke={extStroke} strokeWidth={1.4}/>
          <circle r={18} fill={`rgba(255,119,51,${isRunning ? 0.18 : 0.07})`}/>
          <text y={5} textAnchor="middle" fill={extStroke}
            fontSize={8.5} fontWeight="bold" fontFamily="monospace">
            {(sources[i] ?? `EXT ${i + 1}`).toUpperCase().slice(0, 6)}
          </text>
          <circle r={30} fill="none" stroke={extStroke} strokeWidth={0.9} strokeDasharray="7 4" opacity={0.4}>
            <animateTransform attributeName="transform" type="rotate"
              from={i === 0 ? '0' : '360'} to={i === 0 ? '360' : '0'}
              dur={isRunning ? '6s' : '16s'} repeatCount="indefinite"/>
          </circle>
        </g>
      ))}

      {/* ═══ EVENT-DRIVEN PARTICLES ════════════════════════════════════════
          Key: conditionally mounted so they start/stop with real job events.
          Speed (dur) scales with live throughput — data-driven animation.      */}
      {showEtL && (
        <>
          {/* src1 → processor */}
          <Particle path={P_S1_PROC} dur={pDur} delay="0s"                        r={4.5} fill="#00c8ff" filterId="glow-b"/>
          <Particle path={P_S1_PROC} dur={pDur} delay={`${rawDur*0.33}s`}         r={3}   fill="#00c8ff" opacity={0.55}/>
          <Particle path={P_S1_PROC} dur={pDur} delay={`${rawDur*0.66}s`}         r={2}   fill="#00c8ff" opacity={0.32}/>
          {/* src2 → processor */}
          <Particle path={P_S2_PROC} dur={pDur} delay="0s"                        r={4.5} fill="#00c8ff" filterId="glow-b"/>
          <Particle path={P_S2_PROC} dur={pDur} delay={`${rawDur*0.42}s`}         r={3}   fill="#00c8ff" opacity={0.55}/>
          {/* processor → Local DB (faster — short path) */}
          <Particle path={P_PROC_DB} dur={pDurFast} delay="0s"                    r={4.5} fill="#00ff99" filterId="glow-b"/>
          <Particle path={P_PROC_DB} dur={pDurFast} delay={`${rawDur*0.28}s`}     r={3}   fill="#00ff99" opacity={0.6}/>
          <Particle path={P_PROC_DB} dur={pDurFast} delay={`${rawDur*0.55}s`}     r={2}   fill="#00ff99" opacity={0.35}/>
        </>
      )}
      {showLtE && (
        <>
          {/* Local DB → ext1 */}
          <Particle path={P_DB_E1} dur={pDurSlow} delay="0s"                     r={4}   fill="#ff7733" filterId="glow-s"/>
          <Particle path={P_DB_E1} dur={pDurSlow} delay={`${rawDur*0.5}s`}       r={2.5} fill="#ff7733" opacity={0.55}/>
          {/* Local DB → ext2 */}
          <Particle path={P_DB_E2} dur={`${(rawDur*1.5).toFixed(2)}s`} delay="0s"        r={4}   fill="#ff7733" filterId="glow-s"/>
          <Particle path={P_DB_E2} dur={`${(rawDur*1.5).toFixed(2)}s`} delay={`${rawDur*0.65}s`} r={2.5} fill="#ff7733" opacity={0.55}/>
        </>
      )}

      {/* ═══ Dynamic labels ═══ */}
      <text x={128} y={55} fill="#00c8ff" fontSize={8} fontFamily="monospace" opacity={0.75}>
        READ: {totalRead}
      </text>
      <text x={285} y={97} fill="#00ff99" fontSize={8} fontFamily="monospace" opacity={0.75}>
        WRITE: {totalWritten}
      </text>

      {/* ═══ Status bar ═══ */}
      <g transform="translate(16,254)">
        <rect width={868} height={15} rx={3} fill="rgba(0,200,255,0.04)" stroke="rgba(0,200,255,0.07)" strokeWidth={1}/>
        {successPct && (
          <rect width={Math.round(868 * jobsCompleted / total)} height={15} rx={3}
            fill={isFailed ? 'rgba(255,34,85,0.28)' : 'rgba(0,255,153,0.25)'}/>
        )}
        <text x={434} y={10.5} textAnchor="middle" fill="rgba(200,230,255,0.55)" fontSize={6.5} fontFamily="monospace">
          {state === 'idle'      ? 'AGUARDANDO DADOS…' :
           state === 'running'   ? `SINCRONIZANDO — ${tps.toFixed(0)} reg/s` :
           state === 'completed' ? `CONCLUÍDO ✓  ${successPct ? successPct + '% sucesso' : ''}` :
                                   'FALHA NA SINCRONIZAÇÃO ✗'}
        </text>
      </g>

      {/* ═══ State flash overlays (Framer Motion) ═══ */}
      <AnimatePresence>
        {isCompleted && (
          <motion.rect key="ok" x={0} y={0} width={900} height={280} rx={6}
            fill="rgba(0,255,153,0.07)"
            initial={{ opacity: 0.4 }} animate={{ opacity: 0 }}
            transition={{ duration: 2.4 }}/>
        )}
        {isFailed && (
          <motion.rect key="err" x={0} y={0} width={900} height={280} rx={6}
            fill="rgba(255,34,85,0.1)"
            initial={{ opacity: 0.45 }} animate={{ opacity: 0 }}
            transition={{ duration: 2.8 }}/>
        )}
      </AnimatePresence>
    </svg>
  );
};
