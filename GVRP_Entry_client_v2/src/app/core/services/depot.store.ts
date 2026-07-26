import { computed, inject, Injectable } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { DepotDTO, DepotInputDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { CollectionCache } from '@core/services/collection-cache';

/**
 * Depot Store
 *
 * Shared depot list for the sidebar, the fleet form, the admin screen and the
 * setup guard. No time-based caching: `load()` always refetches, concurrent
 * callers just share the pending request.
 */
@Injectable({ providedIn: 'root' })
export class DepotStore {
  private readonly api = inject(ApiService);

  private readonly cache = new CollectionCache<DepotDTO>({
    fetch: () => this.api.getDepots(),
    fallbackError: 'Could not load depots'
  });

  readonly depots = this.cache.items;
  readonly loading = this.cache.loading;
  readonly error = this.cache.error;
  readonly depotCount = this.cache.count;
  readonly hasDepots = computed(() => !this.cache.isEmpty());

  load(): Observable<readonly DepotDTO[]> {
    return this.cache.load();
  }

  /**
   * Create a depot and merge the result into the shared list.
   * Unlike V1 (`createDepot` swallowed failures inside `handleApiError`),
   * errors propagate so callers can keep the form open.
   */
  create(payload: DepotInputDTO): Observable<DepotDTO> {
    return this.api.createDepot(payload).pipe(
      tap(created => {
        if (created && created.id != null) {
          this.cache.add(created);
        }
      })
    );
  }

  reset(): void {
    this.cache.reset();
  }
}
