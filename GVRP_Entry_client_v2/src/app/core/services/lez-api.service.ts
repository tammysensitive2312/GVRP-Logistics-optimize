import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '@environments/environment';
import {
  BranchZoneDTO,
  CreateBranchZonePayload,
  OrderZoneMembershipDTO,
  RouteGeometryDTO,
  SolutionComplianceDTO,
  VehiclePermitDTO,
  VehiclePermitInput,
  ZoneRuleInput,
  ZoneTemplateDTO,
  ZoneVerdictDTO
} from '@core/models';

/**
 * Every low-emission-zone call (LEZ-001 P1c).
 *
 * Kept apart from `ApiService` so the accepted `ApiService` spies in the
 * existing specs keep working unchanged: only components that opt into LEZ
 * depend on this service.
 */
@Injectable({
  providedIn: 'root'
})
export class LezApiService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = environment.apiUrl;

  // ---------- templates (read-only, system level) ----------

  listTemplates(): Observable<ZoneTemplateDTO[]> {
    return this.http.get<ZoneTemplateDTO[]>(`${this.apiUrl}/zone-templates`);
  }

  getTemplate(templateId: number): Observable<ZoneTemplateDTO> {
    return this.http.get<ZoneTemplateDTO>(`${this.apiUrl}/zone-templates/${templateId}`);
  }

  // ---------- the branch's zones ----------

  listZones(): Observable<BranchZoneDTO[]> {
    return this.http.get<BranchZoneDTO[]>(`${this.apiUrl}/zones`);
  }

  getZone(zoneId: number): Observable<BranchZoneDTO> {
    return this.http.get<BranchZoneDTO>(`${this.apiUrl}/zones/${zoneId}`);
  }

  createZone(payload: CreateBranchZonePayload): Observable<BranchZoneDTO> {
    return this.http.post<BranchZoneDTO>(`${this.apiUrl}/zones`, payload);
  }

  setZoneEnabled(zoneId: number, enabled: boolean): Observable<BranchZoneDTO> {
    return this.http.patch<BranchZoneDTO>(`${this.apiUrl}/zones/${zoneId}`, { enabled });
  }

  /** New name of a COPIED zone, unique within the branch (BROWSER-FIX-001, C7). */
  renameZone(zoneId: number, name: string): Observable<BranchZoneDTO> {
    return this.http.patch<BranchZoneDTO>(`${this.apiUrl}/zones/${zoneId}`, { name });
  }

  /** Raw GeoJSON Polygon, MultiPolygon or Feature; COPIED zones only. */
  replaceZoneGeometry(zoneId: number, geometry: unknown): Observable<BranchZoneDTO> {
    return this.http.put<BranchZoneDTO>(`${this.apiUrl}/zones/${zoneId}/geometry`, geometry);
  }

  /** The whole rule list replaces the previous one; COPIED zones only. */
  replaceZoneRules(zoneId: number, rules: ZoneRuleInput[]): Observable<BranchZoneDTO> {
    return this.http.put<BranchZoneDTO>(`${this.apiUrl}/zones/${zoneId}/rules`, rules);
  }

  acknowledgeZone(zoneId: number, version: number): Observable<BranchZoneDTO> {
    return this.http.post<BranchZoneDTO>(
      `${this.apiUrl}/zones/${zoneId}/acknowledge`,
      { version }
    );
  }

  deleteZone(zoneId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/zones/${zoneId}`);
  }

  /** Rule verdict for one vehicle on one date (`yyyy-MM-dd`). */
  zoneVerdict(zoneId: number, vehicleId: number, date: string): Observable<ZoneVerdictDTO> {
    const params = new HttpParams()
      .set('vehicleId', vehicleId.toString())
      .set('date', date);
    return this.http.get<ZoneVerdictDTO>(`${this.apiUrl}/zones/${zoneId}/verdict`, { params });
  }

  // ---------- permits ----------

  listPermits(vehicleId: number): Observable<VehiclePermitDTO[]> {
    return this.http.get<VehiclePermitDTO[]>(`${this.apiUrl}/vehicles/${vehicleId}/permits`);
  }

  createPermit(vehicleId: number, payload: VehiclePermitInput): Observable<VehiclePermitDTO> {
    return this.http.post<VehiclePermitDTO>(
      `${this.apiUrl}/vehicles/${vehicleId}/permits`,
      payload
    );
  }

  updatePermit(
    vehicleId: number,
    permitId: number,
    payload: VehiclePermitInput
  ): Observable<VehiclePermitDTO> {
    return this.http.put<VehiclePermitDTO>(
      `${this.apiUrl}/vehicles/${vehicleId}/permits/${permitId}`,
      payload
    );
  }

  deletePermit(vehicleId: number, permitId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/vehicles/${vehicleId}/permits/${permitId}`);
  }

  // ---------- compliance and geometry ----------

  solutionCompliance(solutionId: number): Observable<SolutionComplianceDTO> {
    return this.http.get<SolutionComplianceDTO>(
      `${this.apiUrl}/solutions/${solutionId}/compliance`
    );
  }

  recheckCompliance(solutionId: number): Observable<SolutionComplianceDTO> {
    return this.http.post<SolutionComplianceDTO>(
      `${this.apiUrl}/solutions/${solutionId}/compliance/recheck`,
      {}
    );
  }

  routeGeometry(routeId: number): Observable<RouteGeometryDTO> {
    return this.http.get<RouteGeometryDTO>(`${this.apiUrl}/routes/${routeId}/geometry`);
  }

  // ---------- order badge (P1c read endpoint) ----------

  orderZones(orderIds: readonly number[]): Observable<OrderZoneMembershipDTO[]> {
    const params = new HttpParams().set('orderIds', orderIds.join(','));
    return this.http.get<OrderZoneMembershipDTO[]>(`${this.apiUrl}/lez/order-zones`, { params });
  }
}
