import { computed, signal, Signal } from '@angular/core';
import { Observable, of, shareReplay, tap, throwError } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';

export interface CollectionCacheConfig<T> {
  fetch: () => Observable<readonly T[]>;
  /** How long a fetched list stays fresh. V1 `lib/query-cache.js` staleTime. */
  staleTimeMs: number;
  /** Message used when the thrown error carries none. */
  fallbackError: string;
}

/**
 * Signal-backed replacement for V1's `lib/query-cache.js`.
 *
 * One instance holds one cached list: fresh-window caching, in-flight request
 * dedupe, and read-only signals for OnPush components. Stores compose this
 * instead of each re-implementing the same caching dance.
 */
export class CollectionCache<T> {
  private readonly _items = signal<readonly T[]>([]);
  private readonly _loading = signal(false);
  private readonly _error = signal<string | null>(null);

  private loadedAt: number | null = null;
  private inFlight$: Observable<readonly T[]> | null = null;

  readonly items: Signal<readonly T[]> = this._items.asReadonly();
  readonly loading: Signal<boolean> = this._loading.asReadonly();
  readonly error: Signal<string | null> = this._error.asReadonly();
  readonly count = computed(() => this._items().length);
  readonly isEmpty = computed(() => this._items().length === 0);

  constructor(private readonly config: CollectionCacheConfig<T>) {}

  /** Served from cache while fresh; concurrent calls share one request. */
  load(force = false): Observable<readonly T[]> {
    if (!force && this.isFresh()) {
      return of(this._items());
    }

    if (this.inFlight$) {
      return this.inFlight$;
    }

    this._loading.set(true);
    this._error.set(null);

    this.inFlight$ = this.config.fetch().pipe(
      tap(items => {
        this._items.set(items ?? []);
        this.loadedAt = Date.now();
      }),
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
    this.loadedAt = Date.now();
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
    this.loadedAt = Date.now();
  }

  remove(matches: (candidate: T) => boolean): void {
    this._items.update(items => items.filter(candidate => !matches(candidate)));
  }

  /** Mark stale without clearing, so the next load refetches. */
  invalidate(): void {
    this.loadedAt = null;
  }

  reset(): void {
    this._items.set([]);
    this._error.set(null);
    this.loadedAt = null;
    this.inFlight$ = null;
  }

  private isFresh(): boolean {
    return this.loadedAt !== null && Date.now() - this.loadedAt < this.config.staleTimeMs;
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

/** Shared default: V1 cached depots and vehicle types for 10 minutes. */
export const DEFAULT_STALE_TIME_MS = 10 * 60 * 1000;
