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
