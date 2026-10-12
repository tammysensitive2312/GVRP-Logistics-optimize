export type OrderStatus = 'SCHEDULED' | 'ON_ROUTE' | 'SERVICING' | 'COMPLETED' | 'UNASSIGNED' | 'REJECTED';

export interface OrderInputDTO {
  order_code: string;
  customer_name: string;
  customer_phone?: string;
  address: string;
  latitude: number;
  longitude: number;
  demand: number;
  service_time: number;
  time_window_start?: string;
  time_window_end?: string;
  priority: number;
  delivery_notes?: string;
  required_skills?: VehicleSkill[];
}

export interface OrderDTO extends OrderInputDTO {
  id: number;
  branch_id: number;
  delivery_date: string;
  status: OrderStatus;
  created_at: string;
  updated_at: string;
}

export interface OrderFilter {
  date?: string;
  status?: string;
  priority?: number;
  search?: string;
}

export interface BulkStatusRow {
  id: number;
  status?: string;
  order_code?: string;
  reason?: string;
}

export interface BulkStatusResult {
  updated: BulkStatusRow[];
  failed: BulkStatusRow[];
}

export interface BulkStatusPayload {
  ids: number[];
  status: string;
}
import { VehicleSkill } from './vehicle.model';
