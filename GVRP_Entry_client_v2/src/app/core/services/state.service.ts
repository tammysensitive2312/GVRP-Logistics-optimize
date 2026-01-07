import { Injectable } from '@angular/core';
import {OrderFilters} from '@core/models';
import {BehaviorSubject, Observable} from 'rxjs';
import {map} from 'rxjs/operators';

interface AppState {
  selectedOrders: Set<number>;
  selectedVehicles: Set<number>;
  filters: OrderFilters;
  activeSolutionId: number | null;
  activeJobId: number | null;
}

@Injectable({
  providedIn: 'root'
})
export class StateService {

  private state = new BehaviorSubject<AppState>({
    selectedOrders: new Set(),
    selectedVehicles: new Set(),
    filters: { date: '', status: '', priority: '', search: '' },
    activeSolutionId: null,
    activeJobId: null
  });

  public state$ = this.state.asObservable();

  get selectedOrders$(): Observable<Set<number>> {
    return this.state$.pipe(map(s => s.selectedOrders));
  }

  selectOrder(orderId: number): void {
    const current = this.state.value;
    current.selectedOrders.add(orderId);
    this.state.next({ ...current });
  }

  deselectOrder(orderId: number): void {
    const current = this.state.value;
    current.selectedOrders.delete(orderId);
    this.state.next({ ...current });
  }

  constructor() { }
}
