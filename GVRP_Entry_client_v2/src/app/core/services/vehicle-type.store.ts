import { computed, inject, Injectable } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { VehicleTypeDTO, VehicleTypeInputDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { CollectionCache } from '@core/services/collection-cache';

/**
 * Vehicle Type Store
 *
 * Shared vehicle-type list. Mutations update the list in place instead of
 * refetching (V1 invalidated its query key after every create/update).
 */
@Injectable({ providedIn: 'root' })
export class VehicleTypeStore {
  private readonly api = inject(ApiService);

  private readonly cache = new CollectionCache<VehicleTypeDTO>({
    fetch: () => this.api.getVehicleTypes(),
    fallbackError: 'Failed to load vehicle types'
  });

  readonly vehicleTypes = this.cache.items;
  readonly loading = this.cache.loading;
  readonly error = this.cache.error;
  readonly vehicleTypeCount = this.cache.count;
  readonly isEmpty = this.cache.isEmpty;
  readonly hasVehicleTypes = computed(() => !this.cache.isEmpty());

  load(): Observable<readonly VehicleTypeDTO[]> {
    return this.cache.load();
  }

  create(payload: VehicleTypeInputDTO): Observable<VehicleTypeDTO> {
    return this.api.createVehicleType(payload).pipe(
      tap(created => {
        if (created && created.id != null) {
          this.cache.add(created);
        }
      })
    );
  }

  update(typeId: number, payload: VehicleTypeInputDTO): Observable<VehicleTypeDTO> {
    return this.api.updateVehicleType(typeId, payload).pipe(
      tap(updated => {
        if (updated && updated.id != null) {
          this.cache.replace(updated, candidate => candidate.id === typeId);
        }
      })
    );
  }

  remove(typeId: number): Observable<void> {
    return this.api.deleteVehicleType(typeId).pipe(
      tap(() => this.cache.remove(candidate => candidate.id === typeId))
    );
  }

  reset(): void {
    this.cache.reset();
  }
}
