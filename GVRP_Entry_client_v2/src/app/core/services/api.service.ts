import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {environment} from '@environments/environment';
import {OrderDTO, OrderFilter, PaginatedResponse} from '@core/models';
import {Observable} from 'rxjs';

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
}
