import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {environment} from '@environments/environment';
import {DepotDTO, OrderDTO, OrderFilter, PaginatedResponse, VehicleDTO} from '@core/models';
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
}
