import React, { useEffect, useRef } from 'react';
import {
  Area, AreaChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';

const MAX_POINTS = 60;

interface Props {
  throughput: number;
}

export const ThroughputChart: React.FC<Props> = ({ throughput }) => {
  const bufferRef = useRef<{ v: number }[]>(Array.from({ length: MAX_POINTS }, () => ({ v: 0 })));
  const [, forceRender] = React.useState(0);

  useEffect(() => {
    bufferRef.current = [...bufferRef.current.slice(1), { v: Math.round(throughput) }];
    forceRender(n => n + 1);
  }, [throughput]);

  return (
    <div className="chart-panel glass">
      <div className="panel-title">Throughput (reg/s)</div>
      <div className="chart-wrap">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={bufferRef.current} margin={{ top: 4, right: 4, left: -10, bottom: 0 }}>
            <defs>
              <linearGradient id="tpsGrad" x1="0" y1="0" x2="0" y2="1">
                <stop offset="10%" stopColor="#00c8ff" stopOpacity={0.22}/>
                <stop offset="95%" stopColor="#00c8ff" stopOpacity={0.01}/>
              </linearGradient>
            </defs>
            <XAxis dataKey="" hide/>
            <YAxis
              tick={{ fill: 'rgba(180,220,255,0.4)', fontSize: 10, fontFamily: 'monospace' }}
              tickLine={false} axisLine={false}
              width={36}
            />
            <Tooltip
              contentStyle={{
                background: 'rgba(8,18,30,0.92)',
                border: '1px solid rgba(0,200,255,0.2)',
                borderRadius: 6,
                fontSize: 11,
                fontFamily: 'monospace',
                color: '#00c8ff',
              }}
              itemStyle={{ color: '#00c8ff' }}
              formatter={(v: number) => [`${v} reg/s`, 'throughput']}
              labelFormatter={() => ''}
            />
            <Area
              type="monotone" dataKey="v" dot={false}
              stroke="#00c8ff" strokeWidth={2}
              fill="url(#tpsGrad)"
              isAnimationActive={false}
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};
