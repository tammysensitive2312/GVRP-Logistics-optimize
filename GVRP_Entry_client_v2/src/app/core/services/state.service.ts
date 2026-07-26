import { Injectable } from '@angular/core';
import { OrderFilter } from '@core/models';
import { BehaviorSubject, Observable } from 'rxjs';
import { distinctUntilChanged, map } from 'rxjs/operators';

interface AppState {
  selectedOrders: ReadonlySet<number>;
  selectedVehicles: ReadonlySet<number>;
  filters: OrderFilter;
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
    // `priority` is optional and numeric (OrderFilter); '' would not type-check
    // and an unset filter is better expressed by leaving the key out.
    filters: { date: '', status: '', search: '' },
    activeSolutionId: null,
    activeJobId: null
  });

  public state$ = this.state.asObservable();

  get selectedOrders$(): Observable<ReadonlySet<number>> {
    return this.state$.pipe(
      map(s => s.selectedOrders),
      distinctUntilChanged()
    );
  }

  /**
   * Selection updates always emit a NEW Set.
   * Mutating the existing Set and re-emitting `{...current}` kept the Set
   * identity unchanged, so OnPush views and distinctUntilChanged consumers
   * could miss the change.
   */
  selectOrder(orderId: number): void {
    this.patchSelectedOrders(prev => new Set([...prev, orderId]));
  }

  deselectOrder(orderId: number): void {
    this.patchSelectedOrders(prev => {
      const next = new Set(prev);
      next.delete(orderId);
      return next;
    });
  }

  private patchSelectedOrders(
    project: (prev: ReadonlySet<number>) => ReadonlySet<number>
  ): void {
    const current = this.state.value;
    this.state.next({ ...current, selectedOrders: project(current.selectedOrders) });
  }
}
