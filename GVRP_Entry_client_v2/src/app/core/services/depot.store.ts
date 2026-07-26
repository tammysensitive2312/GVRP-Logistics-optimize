import { computed, inject, Injectable } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { DepotDTO, DepotInputDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';
import {
  CollectionCache,
  DEFAULT_STALE_TIME_MS
} from '@core/services/collection-cache';

/**
 * Depot Store
 *
 * Replaces V1's `QueryKeys.depots.all` cache entry (staleTime 10 minutes,
 * "depots rarely change") with a signal-based cache.
 */
@Injectable({ providedIn: 'root' })
export class DepotStore {
  private readonly api = inject(ApiService);

  private readonly cache = new CollectionCache<DepotDTO>({
    fetch: () => this.api.getDepots(),
    staleTimeMs: DEFAULT_STALE_TIME_MS,
    fallbackError: 'Không thể tải danh sách depot.'
  });

  readonly depots = this.cache.items;
  readonly loading = this.cache.loading;
  readonly error = this.cache.error;
  readonly depotCount = this.cache.count;
  readonly hasDepots = computed(() => !this.cache.isEmpty());

  load(force = false): Observable<readonly DepotDTO[]> {
    return this.cache.load(force);
  }

  /**
   * Create a depot and merge the result into the cache.
   * Unlike V1 (`createDepot` swallowed failures inside `handleApiError`),
   * errors propagate so callers can keep the form open.
   */
  create(payload: DepotInputDTO): Observable<DepotDTO> {
    return this.api.createDepot(payload).pipe(
      tap(created => {
        if (created && created.id != null) {
          this.cache.add(created);
        } else {
          // Unexpected shape - drop freshness so the next read refetches.
          this.cache.invalidate();
        }
      })
    );
  }

  invalidate(): void {
    this.cache.invalidate();
  }

  reset(): void {
    this.cache.reset();
  }
}
