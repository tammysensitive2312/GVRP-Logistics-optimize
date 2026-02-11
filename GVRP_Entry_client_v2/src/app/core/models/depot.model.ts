export interface DepotInputDTO {
  id: number;
  name: string;
  address: string;
  latitude: number;
  longitude: number;
}

export interface DepotDTO extends DepotInputDTO {
  branch_id: number;
  created_at: string;
  updated_at: string;
}
