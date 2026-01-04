export interface VehicleTypeDTO {
  id: number;
  name: string;
  capacity: number;
  fixed_cost: number;
  cost_per_km: number;
  cost_per_hour: number;
  max_distance?: number;
  max_duration?: number;
  vehicle_features?: any;
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
