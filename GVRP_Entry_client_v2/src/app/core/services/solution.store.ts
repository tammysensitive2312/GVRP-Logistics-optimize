import { computed, inject, Injectable, signal } from '@angular/core';
import { Observable, tap, throwError } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';

import { SolutionDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';

/**
 * Solution Store
 *
 * Single source of truth for the solution currently displayed on the dashboard;
 * the map, the route view and the timeline view all read from here.
 *
 * Intentionally in-memory only. An earlier version persisted `activeSolutionId`
 * to localStorage (mirroring V1's `persistence-manager.js`) and refetched it on
 * boot, which meant a reload silently redrew a solution the user had not asked
 * for, and a deleted solution left a stale id behind. Re-opening a past run is
 * now an explicit action on the /jobs screen.
 */
@Injectable({ providedIn: 'root' })
export class SolutionStore {
  private readonly api = inject(ApiService);

  private readonly _solution = signal<SolutionDTO | null>(null);
  private readonly _loading = signal(false);

  readonly solution = this._solution.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly hasSolution = computed(() => this._solution() !== null);
  readonly activeSolutionId = computed(() => this._solution()?.id ?? null);

  /** Called when a job finishes or a run is picked from job history. */
  setSolution(solution: SolutionDTO): void {
    this._solution.set(solution);
  }

  loadById(solutionId: number): Observable<SolutionDTO> {
    this._loading.set(true);

    return this.api.getSolutionById(solutionId).pipe(
      tap(solution => this.setSolution(solution)),
      catchError((error: unknown) => {
        this.clear();
        return throwError(() => error);
      }),
      finalize(() => this._loading.set(false))
    );
  }

  clear(): void {
    this._solution.set(null);
  }
}
