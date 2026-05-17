import { useEffect, useRef, useState } from 'react';
import type {
  JobCompletedEvent, JobStartedEvent,
  PipelineState, Snapshot, SyncDirection,
} from '../types';

interface SyncStreamResult {
  snapshot: Snapshot | null;
  pipelineState: PipelineState;
  activeSource: string | null;
  activeDirection: SyncDirection | null;
  connected: boolean;
}

export function useSyncStream(): SyncStreamResult {
  const [snapshot,        setSnapshot]        = useState<Snapshot | null>(null);
  const [pipelineState,   setPipelineState]   = useState<PipelineState>('idle');
  const [activeSource,    setActiveSource]    = useState<string | null>(null);
  const [activeDirection, setActiveDirection] = useState<SyncDirection | null>(null);
  const [connected,       setConnected]       = useState(false);

  const resetTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    let es: EventSource;
    let retryTimer: ReturnType<typeof setTimeout>;

    const scheduleReset = (delay: number) => {
      if (resetTimer.current) clearTimeout(resetTimer.current);
      resetTimer.current = setTimeout(() => {
        setPipelineState('idle');
        setActiveSource(null);
        setActiveDirection(null);
      }, delay);
    };

    const connect = () => {
      es = new EventSource('/monitor/stream');

      es.addEventListener('snapshot', (e: MessageEvent) => {
        try { setSnapshot(JSON.parse(e.data) as Snapshot); } catch { /* ignore */ }
      });

      es.addEventListener('job-started', (e: MessageEvent) => {
        try {
          const d = JSON.parse(e.data) as JobStartedEvent;
          if (resetTimer.current) clearTimeout(resetTimer.current);
          setActiveSource(d.source);
          setActiveDirection(d.direction);
          setPipelineState('running');
        } catch { /* ignore */ }
      });

      es.addEventListener('job-completed', (e: MessageEvent) => {
        try {
          const d = JSON.parse(e.data) as JobCompletedEvent;
          setActiveSource(d.source);
          setPipelineState('completed');
          scheduleReset(2800);
        } catch { /* ignore */ }
      });

      es.addEventListener('job-failed', () => {
        setPipelineState('failed');
        scheduleReset(3200);
      });

      es.onopen = () => {
        setConnected(true);
        clearTimeout(retryTimer);
      };

      es.onerror = () => {
        setConnected(false);
        es.close();
        // immediate fallback poll
        fetch('/monitor/status')
          .then(r => r.json())
          .then(d => setSnapshot(d as Snapshot))
          .catch(() => { /* ignore */ });
        retryTimer = setTimeout(connect, 3000);
      };
    };

    connect();

    return () => {
      es?.close();
      clearTimeout(retryTimer);
      if (resetTimer.current) clearTimeout(resetTimer.current);
    };
  }, []);

  return { snapshot, pipelineState, activeSource, activeDirection, connected };
}
