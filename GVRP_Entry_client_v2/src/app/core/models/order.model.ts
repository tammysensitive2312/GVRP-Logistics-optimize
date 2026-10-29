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

/**
 * Body of an order create/update as the dialog sends it (UX-ORDERS-001, C1):
 * empty optional fields are `null`, and missing coordinates are `null` so the
 * server geocodes the address.
 */
export type OrderWritePayload = Omit<OrderInputDTO,
  'latitude' | 'longitude' | 'customer_phone' | 'delivery_notes' | 'time_window_start' | 'time_window_end'> & {
  latitude: number | null;
  longitude: number | null;
  customer_phone: string | null;
  delivery_notes: string | null;
  time_window_start: string | null;
  time_window_end: string | null;
  delivery_date?: string;
  status?: string;
};

/** One refused import row; `raw_data` may hold customer data and is never shown. */
export interface ImportErrorDTO {
  line_number: number | null;
  order_code: string | null;
  field: string | null;
  error_message: string;
  raw_data: string | null;
}

export interface ImportResultDTO {
  success: boolean;
  total_records: number;
  imported_count: number;
  skipped_count: number;
  errors: ImportErrorDTO[];
  requires_confirmation: boolean;
}
