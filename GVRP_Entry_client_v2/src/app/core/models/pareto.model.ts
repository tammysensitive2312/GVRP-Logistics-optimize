/**
 * Pareto frontier view models (PARETO-002).
 *
 * Mirrors `ParetoFrontierDTO` / `ParetoCandidateDTO` of GVRP_Entry_API
 * (PARETO-001) serialised with the global snake_case naming strategy.
 */

export type FrontierStatus = 'FRONTIER' | 'DOMINATED' | 'SERVICE_DOMINATED';

export type NotApplicableReason =
  | 'NOT_ON_FRONTIER'
  | 'NO_PLAN'
  | 'ALREADY_ACTIVE'
  | 'ORDER_SET_DIFFERS';

export interface ParetoCandidateDTO {
  id: number;
  index: number;
  label: string;
  cost_weight: number;
  co2_weight: number;
  total_cost: number;
  total_distance: number;
  total_co2: number;
  total_time: number;
  total_vehicles_used: number;
  served_orders: number;
  unserved_orders: number;
  frontier_status: FrontierStatus;
  engine_selected: boolean;
  applicable: boolean;
  not_applicable_reason: NotApplicableReason | null;
}

export interface ParetoFrontierDTO {
  job_id: number | null;
  solution_id: number;
  latest_solution_id: number;
  active_candidate_id: number | null;
  clustered: boolean;
  cluster_count: number | null;
  candidates: ParetoCandidateDTO[];
}
