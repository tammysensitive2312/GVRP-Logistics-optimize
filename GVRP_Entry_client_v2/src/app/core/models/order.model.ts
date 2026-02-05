export type OrderStatus = 'SCHEDULED' | 'ON_ROUTE' | 'COMPLETED' | 'FAILED';

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
  date: string;
  status?: string;
  priority?: string;
  search?: string;
}
