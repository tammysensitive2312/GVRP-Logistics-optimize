export interface StopDTO {
  sequence_number?: number;
  type: 'DEPOT' | 'ORDER';
  location_name: string;
  latitude: number;
  longitude: number;
  arrival_time: string;
  departure_time?: string;
  demand?: number;
  load_after: number;
  wait_time: number;
}

export interface RouteDTO {
  vehicle_id: number;
  vehicle_license_plate: string;
  start_time: string;
  end_time: string;
  distance: number;
  service_time: number;
  order_count: number;
  load_utilization: number;
  stops: StopDTO[];
}

export interface SolutionDTO {
  id: number;
  job_id: number;
  total_cost: number;
  total_distance: number;
  total_time: number;
  total_co2: number;
  total_vehicles_used: number;
  served_orders: number;
  unserved_orders: number;
  routes: RouteDTO[];
}
