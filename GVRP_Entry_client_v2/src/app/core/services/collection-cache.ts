import { computed, signal, Signal } from '@angular/core';
import { Observable, shareReplay, tap, throwError } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';

export interface CollectionCacheConfig<T> {
  fetch: () => Observable<readonly T[]>;
  /** Message used when the thrown error carries none. */
  fallbackError: string;
}

/**
 * Shared list state with in-flight request de-duplication.
 *
 * Deliberately NOT a time-based cache. An earlier version kept results "fresh"
 * for 10 minutes (mirroring V1's `lib/query-cache.js` staleTime) which meant a
 * list could silently lag behind the backend after a change made in another tab
 * or by another user. Every `load()` now hits the API; the only thing shared is
 * a request that is still in flight, which matters because a guard and a screen
 * routinely ask for the same list in the same tick.
 */
export class CollectionCache<T> {
  private readonly _items = signal<readonly T[]>([]);
  private readonly _loading = signal(false);
  private readonly _error = signal<string | null>(null);

  private inFlight$: Observable<readonly T[]> | null = null;

  readonly items: Signal<readonly T[]> = this._items.asReadonly();
  readonly loading: Signal<boolean> = this._loading.asReadonly();
  readonly error: Signal<string | null> = this._error.asReadonly();
  readonly count = computed(() => this._items().length);
  readonly isEmpty = computed(() => this._items().length === 0);

  constructor(private readonly config: CollectionCacheConfig<T>) {}

  /** Always refetches; concurrent callers share the pending request. */
  load(): Observable<readonly T[]> {
    if (this.inFlight$) {
      return this.inFlight$;
    }

    this._loading.set(true);
    this._error.set(null);

    this.inFlight$ = this.config.fetch().pipe(
      tap(items => this._items.set(items ?? [])),
      catchError((error: unknown) => {
        this._error.set(resolveErrorMessage(error, this.config.fallbackError));
        return throwError(() => error);
      }),
      finalize(() => {
        this._loading.set(false);
        this.inFlight$ = null;
      }),
      shareReplay({ bufferSize: 1, refCount: false })
    );

    return this.inFlight$;
  }

  /** Append an item created server-side. Always emits a new array. */
  add(item: T): void {
    this._items.update(items => [...items, item]);
  }

  /** Replace the first item matching `matches`; appends when nothing matches. */
  replace(item: T, matches: (candidate: T) => boolean): void {
    this._items.update(items => {
      const index = items.findIndex(matches);
      if (index === -1) return [...items, item];

      const next = [...items];
      next[index] = item;
      return next;
    });
  }

  remove(matches: (candidate: T) => boolean): void {
    this._items.update(items => items.filter(candidate => !matches(candidate)));
  }

  reset(): void {
    this._items.set([]);
    this._error.set(null);
    this.inFlight$ = null;
  }
}

export function resolveErrorMessage(error: unknown, fallback: string): string {
  if (typeof error === 'object' && error !== null && 'message' in error) {
    const message = (error as { message?: unknown }).message;
    if (typeof message === 'string' && message.trim().length > 0) {
      return message;
    }
  }
  return fallback;
}
