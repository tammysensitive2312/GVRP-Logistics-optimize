import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {environment} from '@environments/environment';
import {
  DepotDTO,
  DepotInputDTO,
  FleetDTO,
  FleetInputDTO,
  JobDTO,
  OrderDTO,
  OrderFilter,
  OrderInputDTO,
  PaginatedResponse,
  RoutePlanningRequest,
  SolutionDTO,
  VehicleDTO,
  VehicleTypeDTO,
  VehicleTypeInputDTO
} from '@core/models';
import {Observable} from 'rxjs';
import {map} from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  getOrders(filters: OrderFilter, page: number = 1, pageSize: number = 20): Observable<PaginatedResponse<OrderDTO>> {
    let params = new HttpParams()
      .set('date', filters.date)
      .set('page', page.toString())
      .set('size', pageSize.toString());

    if (filters.status) params = params.set('status', filters.status);
    if (filters.priority) params = params.set('priority', filters.priority);
    if (filters.search) params = params.set('search', filters.search);

    return this.http.get<PaginatedResponse<OrderDTO>>(`${this.apiUrl}/orders`, {params});
  }

  getVehicles(page: number = 0, size: number = 1000): Observable<VehicleDTO[]> {
    return this.http.get<PaginatedResponse<VehicleDTO>>(
      `${this.apiUrl}/vehicles?page=${page}&size=${size}`
    ).pipe(
      map(res => res.content || [])
    );
  }

  getDepots(): Observable<DepotDTO[]> {
    return this.http.get<DepotDTO[]>(`${this.apiUrl}/depots`);
  }

  createDepot(depot: DepotInputDTO): Observable<DepotDTO> {
    return this.http.post<DepotDTO>(`${this.apiUrl}/depots`, depot);
  }

  // ============================================
  // VEHICLE TYPES
  // ============================================

  getVehicleTypes(): Observable<VehicleTypeDTO[]> {
    return this.http.get<VehicleTypeDTO[]>(`${this.apiUrl}/vehicle-types`);
  }

  createVehicleType(vehicleType: VehicleTypeInputDTO): Observable<VehicleTypeDTO> {
    return this.http.post<VehicleTypeDTO>(`${this.apiUrl}/vehicle-types`, vehicleType);
  }

  updateVehicleType(
    typeId: number,
    vehicleType: VehicleTypeInputDTO
  ): Observable<VehicleTypeDTO> {
    return this.http.put<VehicleTypeDTO>(
      `${this.apiUrl}/vehicle-types/${typeId}`,
      vehicleType
    );
  }

  /**
   * V1's admin screen called a global `deleteVehicleType()` that was never
   * defined in scripts/api/api.js, so its delete button always threw. This
   * follows the REST shape of the other vehicle-type endpoints - confirm the
   * backend actually exposes DELETE /vehicle-types/{id}.
   */
  deleteVehicleType(typeId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/vehicle-types/${typeId}`);
  }

  // ============================================
  // FLEETS
  // ============================================

  getFleets(): Observable<FleetDTO[]> {
    return this.http.get<FleetDTO[]>(`${this.apiUrl}/fleets`);
  }

  createFleet(fleet: FleetInputDTO): Observable<FleetDTO> {
    return this.http.post<FleetDTO>(`${this.apiUrl}/fleets`, fleet);
  }

  updateOrder(orderId: number, updateData: OrderInputDTO): Observable<OrderDTO> {
    return this.http.put<OrderDTO>(
      `${this.apiUrl}/orders/${orderId}`,
      updateData
    );
  }

  addOrder(createData: OrderInputDTO): Observable<OrderDTO> {
    return this.http.post<OrderDTO>(
      `${this.apiUrl}/orders`,
      createData
    )
  }

  getOrderById(orderId: number): Observable<OrderDTO> {
    return this.http.get<OrderDTO>(
      `${this.apiUrl}/orders/${orderId}`
    );
  }

  submitRoutePlanningJob(request: RoutePlanningRequest): Observable<JobDTO> {
    return this.http.post<JobDTO>(`${this.apiUrl}/jobs/plan`, request);
  }

  getJobById(jobId: number): Observable<JobDTO> {
    return this.http.get<JobDTO>(`${this.apiUrl}/jobs/${jobId}`);
  }

  /**
   * V1 `getCurrentRunningJob()`: GET /jobs/current answers 204 when nothing is
   * running, so an empty body maps to null instead of an error.
   */
  getCurrentRunningJob(): Observable<JobDTO | null> {
    return this.http
      .get<JobDTO>(`${this.apiUrl}/jobs/current`, { observe: 'response' })
      .pipe(map(response => (response.status === 204 ? null : response.body)));
  }

  /**
   * V1 `getJobHistory(limit)` accepted a limit but never sent it - it just
   * called GET /jobs. The parameter is passed through here.
   */
  getJobHistory(limit = 10): Observable<JobDTO[]> {
    const params = new HttpParams().set('limit', limit.toString());
    return this.http.get<JobDTO[]>(`${this.apiUrl}/jobs`, { params });
  }

  cancelJob(jobId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/jobs/${jobId}`);
  }

  getSolutionById(solutionId: number): Observable<SolutionDTO> {
    return this.http.get<SolutionDTO>(`${this.apiUrl}/solutions/${solutionId}`);
  }

}
