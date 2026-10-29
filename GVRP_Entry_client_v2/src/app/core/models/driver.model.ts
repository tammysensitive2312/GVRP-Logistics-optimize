import type { RouteLegInstructionDTO } from './lez.model';

export interface DriverDTO {
  id: number;
  username: string;
  full_name: string | null;
  phone: string | null;
}

export interface RouteSendDTO {
  id: number;
  solution_id: number;
  route_id: number;
  vehicle_license_plate: string | null;
  driver_id: number;
  driver_name: string;
  sender_name: string;
  sent_at: string;
  status: 'CURRENT' | 'SUPERSEDED';
}

export interface SendRoutePayload {
  driver_user_id: number;
  solution_id: number;
}

export interface DriverStopDTO {
  sequence_number: number | null;
  type: string;
  order_id: number | null;
  location_name: string;
  address: string | null;
  latitude: number | null;
  longitude: number | null;
  arrival_time: string | null;
  departure_time: string | null;
  customer_name: string | null;
  customer_phone: string | null;
  delivery_notes: string | null;
  demand: number | null;
  /** Encoded polyline of the leg to the next stop (LEZ-001, D1); absent on old data. */
  geometry_to_next?: string | null;
  /** Turn-by-turn instructions of that leg; absent on old data. */
  instructions_to_next?: RouteLegInstructionDTO[] | null;
  /** Enabled low-emission zones covering this stop; absent on old data. */
  zone_names?: string[] | null;
}

export interface DriverRouteDTO {
  send_id: number;
  solution_id: number;
  route_id: number;
  vehicle_license_plate: string | null;
  status: 'CURRENT' | 'SUPERSEDED';
  stops: DriverStopDTO[];
}

export interface RevisionDTO {
  id: number;
  created_at: string;
  type: string | null;
  status: string | null;
}
