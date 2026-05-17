export interface AgentComment {
  icon: string;
  text: string;
  severity: 'SUCCESS' | 'INFO' | 'WARNING' | 'ERROR';
  context: string | null;
  timestamp: string;
}

export interface Snapshot {
  totalRead: number;
  totalWritten: number;
  totalFiltered: number;
  totalSkipped: number;
  jobsCompleted: number;
  jobsFailed: number;
  throughputPerSec: number;
  avgDurationMs: number;
  lastDurationMs: number;
  sourceStatus: Record<string, string>;
  lastActivity: string;
  idle: boolean;
  recentComments: AgentComment[];
  recentLogs: string[];
}

export type PipelineState = 'idle' | 'running' | 'completed' | 'failed';
export type SyncDirection = 'external-to-local' | 'local-to-external';

export interface JobStartedEvent {
  source: string;
  job: string;
  direction: SyncDirection;
}

export interface JobCompletedEvent {
  source: string;
  durationMs: number;
  throughput: number;
  writeCount: number;
}

export interface StepProgressEvent {
  read: number;
  write: number;
  filter: number;
  source: string;
}
