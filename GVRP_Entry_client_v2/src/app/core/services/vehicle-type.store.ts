import { computed, inject, Injectable } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { VehicleTypeDTO, VehicleTypeInputDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';
import {
  CollectionCache,
  DEFAULT_STALE_TIME_MS
} from '@core/services/collection-cache';

/**
 * Vehicle Type Store
 *
 * Replaces V1's `QueryKeys.vehicleTypes.all` cache entry. V1 invalidated that
 * key after every create/update; here the cache is updated in place instead.
 */
@Injectable({ providedIn: 'root' })
export class VehicleTypeStore {
  private readonly api = inject(ApiService);

  private readonly cache = new CollectionCache<VehicleTypeDTO>({
    fetch: () => this.api.getVehicleTypes(),
    staleTimeMs: DEFAULT_STALE_TIME_MS,
    fallbackError: 'Không thể tải danh sách loại xe'
  });

  readonly vehicleTypes = this.cache.items;
  readonly loading = this.cache.loading;
  readonly error = this.cache.error;
  readonly vehicleTypeCount = this.cache.count;
  readonly isEmpty = this.cache.isEmpty;
  readonly hasVehicleTypes = computed(() => !this.cache.isEmpty());

  load(force = false): Observable<readonly VehicleTypeDTO[]> {
    return this.cache.load(force);
  }

  create(payload: VehicleTypeInputDTO): Observable<VehicleTypeDTO> {
    return this.api.createVehicleType(payload).pipe(
      tap(created => {
        if (created && created.id != null) {
          this.cache.add(created);
        } else {
          this.cache.invalidate();
        }
      })
    );
  }

  update(typeId: number, payload: VehicleTypeInputDTO): Observable<VehicleTypeDTO> {
    return this.api.updateVehicleType(typeId, payload).pipe(
      tap(updated => {
        if (updated && updated.id != null) {
          this.cache.replace(updated, candidate => candidate.id === typeId);
        } else {
          this.cache.invalidate();
        }
      })
    );
  }

  remove(typeId: number): Observable<void> {
    return this.api.deleteVehicleType(typeId).pipe(
      tap(() => this.cache.remove(candidate => candidate.id === typeId))
    );
  }

  invalidate(): void {
    this.cache.invalidate();
  }

  reset(): void {
    this.cache.reset();
  }
}
