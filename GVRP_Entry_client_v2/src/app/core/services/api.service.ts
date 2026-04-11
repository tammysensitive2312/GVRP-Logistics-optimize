import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {environment} from '@environments/environment';
import {
  DepotDTO, JobDTO,
  OrderDTO,
  OrderFilter,
  OrderInputDTO,
  PaginatedResponse,
  RoutePlanningRequest,
  SolutionDTO,
  VehicleDTO
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

  cancelJob(jobId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/jobs/${jobId}`);
  }

  getSolutionById(solutionId: number): Observable<SolutionDTO> {
    return this.http.get<SolutionDTO>(`${this.apiUrl}/solutions/${solutionId}`);
  }

}
