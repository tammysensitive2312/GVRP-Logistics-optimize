/**
 * Payload sent to POST /depots.
 * Mirrors V1 `createDepot(depotData)` in scripts/api/api.js, which posts
 * exactly { name, address, latitude, longitude } - no id.
 */
export interface DepotInputDTO {
  name: string;
  address: string;
  latitude: number;
  longitude: number;
}

/** Depot as returned by the backend. */
export interface DepotDTO extends DepotInputDTO {
  id: number;
  branch_id: number;
  created_at: string;
  updated_at: string;
}
