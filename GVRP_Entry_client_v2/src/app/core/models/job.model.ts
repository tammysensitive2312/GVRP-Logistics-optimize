export type JobStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface JobDTO {
  id: number;
  status: JobStatus;
  progress: number;
  created_at: string;
  started_at?: string;
  completed_at?: string;
  error_message?: string;
  solution_id?: number;
}

/**
 * GET /jobs/{id}/progress
 *
 * Live solver telemetry. Note this endpoint returns a raw map, so the keys are
 * camelCase while every other DTO in this app is snake_case. Fields depend on
 * the phase: BUILDING_MATRIX only reports phase/status/timestamps, SOLVING adds
 * the iteration counters. The endpoint answers 204 when there is no progress
 * record (job finished and telemetry cleared, or never started).
 */
export interface JobProgressDTO {
  jobId: number;
  /** e.g. 'BUILDING_MATRIX' | 'SOLVING'. Left open - the solver may add phases. */
  phase: string;
  /** 'RUNNING' while working; anything else is treated as terminal. */
  status: string;
  startedAt: string;
  updatedAt?: string;
  finishedAt?: string | null;
  percent?: number;
  iteration?: number;
  maxIterations?: number;
  routes?: number;
  unassigned?: number;
  bestCost?: number;
  elapsedSeconds?: number;
}

export interface RoutePlanningRequest {
  order_ids: number[];
  vehicle_ids: number[];
  preferences: {
    goal: string;
    speed: string;
    allow_unassigned_orders: boolean;
    time_window_mode: string;
    enable_pareto_analysis: boolean;
  };
}
