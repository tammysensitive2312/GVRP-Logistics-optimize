import { computed, inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';
import { throwError } from 'rxjs';

import { SolutionDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { StorageService } from '@core/services/storage.service';

/**
 * Solution Store
 *
 * Single source of truth for the currently displayed solution. Replaces V1's
 * `SolutionDisplay` static class plus the `AppState.activeSolutionId` bookkeeping
 * in `persistence-manager.js`.
 *
 * The map, the route view and the timeline view all read from here, which is why
 * the solution now actually reaches the map - previously `main.component.solution`
 * was declared but never assigned.
 */
@Injectable({ providedIn: 'root' })
export class SolutionStore {
  private readonly api = inject(ApiService);
  private readonly storage = inject(StorageService);

  private readonly _solution = signal<SolutionDTO | null>(null);
  private readonly _loading = signal(false);

  readonly solution = this._solution.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly hasSolution = computed(() => this._solution() !== null);
  readonly activeSolutionId = computed(() => this._solution()?.id ?? null);

  /** Called when a job finishes or a solution is picked from history. */
  setSolution(solution: SolutionDTO): void {
    this._solution.set(solution);
    this.storage.updateAppState({ activeSolutionId: solution.id });
  }

  /** V1 restored `activeSolutionId` from storage on boot. */
  loadById(solutionId: number): Observable<SolutionDTO> {
    this._loading.set(true);

    return this.api.getSolutionById(solutionId).pipe(
      tap(solution => this.setSolution(solution)),
      catchError((error: unknown) => {
        // V1 dropped the stored id when restoring failed, so a stale id does not
        // keep breaking every reload.
        this.clear();
        return throwError(() => error);
      }),
      finalize(() => this._loading.set(false))
    );
  }

  /** Restores the persisted solution, or completes without emitting. */
  restorePersisted(): Observable<SolutionDTO> | null {
    const solutionId = this.storage.getAppState()?.activeSolutionId;
    return solutionId ? this.loadById(solutionId) : null;
  }

  clear(): void {
    this._solution.set(null);
    this.storage.updateAppState({ activeSolutionId: undefined });
  }
}
