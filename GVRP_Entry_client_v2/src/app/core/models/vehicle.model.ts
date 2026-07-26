/**
 * Free-form feature bag the backend stores per vehicle type.
 * V1 (`vehicle-type-form.js`) only ever set `emission_factor`.
 */
export interface VehicleFeatures {
  emission_factor?: number;
}

/**
 * Payload for POST/PUT /vehicle-types.
 *
 * Note the asymmetry, kept because it is what the backend expects:
 * the write payload uses `type_name`, while the read model returns `name`.
 */
export interface VehicleTypeInputDTO {
  type_name: string;
  capacity: number;
  fixed_cost: number;
  cost_per_km: number;
  cost_per_hour: number;
  max_distance?: number;
  max_duration?: number;
  vehicle_features?: VehicleFeatures;
}

export interface VehicleTypeDTO {
  id: number;
  name: string;
  capacity: number;
  fixed_cost: number;
  cost_per_km: number;
  cost_per_hour: number;
  max_distance?: number;
  max_duration?: number;
  vehicle_features?: VehicleFeatures;
}

export interface VehicleInputDTO {
  vehicle_license_plate: string;
  vehicle_type_id: number;
  start_depot_id: number;
  end_depot_id: number;
}

export interface VehicleDTO extends VehicleInputDTO {
  id: number;
  branch_id: number;
  vehicle_type_name: string;
  capacity: number;
  status: 'AVAILABLE' | 'IN_USE';
}

export interface FleetInputDTO {
  fleet_name: string;
  vehicles: VehicleInputDTO[];
}

export interface FleetDTO {
  id: number;
  fleet_name: string;
  branch_id: number;
  vehicle_count: number;
}
