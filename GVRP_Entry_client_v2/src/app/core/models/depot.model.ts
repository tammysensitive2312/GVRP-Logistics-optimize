export interface DepotInputDTO {
  name: string;
  address: string;
  latitude: number;
  longitude: number;
}

export interface DepotDTO extends DepotInputDTO {
  id: number;
  branch_id: number;
  created_at: string;
  updated_at: string;
}
