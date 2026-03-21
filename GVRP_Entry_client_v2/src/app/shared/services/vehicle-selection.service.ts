import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class VehicleSelectionService {
  private selectedIds$ = new BehaviorSubject<number[]>([]);

  selectedVehicleIds$ = this.selectedIds$.asObservable();

  setSelection(ids: number[]): void {
    this.selectedIds$.next(ids);
  }

  getCount(): number {
    return this.selectedIds$.getValue().length;
  }
}
