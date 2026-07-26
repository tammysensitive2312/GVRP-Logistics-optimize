import { Observable, of, throwError } from 'rxjs';

import { CollectionCache } from './collection-cache';

interface Item {
  id: number;
  name: string;
}

describe('CollectionCache', () => {
  const itemA: Item = { id: 1, name: 'A' };
  const itemB: Item = { id: 2, name: 'B' };

  let fetchCalls: number;
  let response: Observable<readonly Item[]>;
  let cache: CollectionCache<Item>;

  const build = (staleTimeMs = 10_000) =>
    new CollectionCache<Item>({
      fetch: () => {
        fetchCalls++;
        return response;
      },
      staleTimeMs,
      fallbackError: 'fallback message'
    });

  beforeEach(() => {
    fetchCalls = 0;
    response = of([itemA]);
    cache = build();
  });

  it('fetches on first load and exposes the items', done => {
    cache.load().subscribe(items => {
      expect(items).toEqual([itemA]);
      expect(cache.items()).toEqual([itemA]);
      expect(cache.count()).toBe(1);
      expect(cache.isEmpty()).toBeFalse();
      expect(fetchCalls).toBe(1);
      done();
    });
  });

  it('serves the cache while fresh instead of refetching', done => {
    cache.load().subscribe(() => {
      cache.load().subscribe(() => {
        expect(fetchCalls).toBe(1);
        done();
      });
    });
  });

  it('refetches when forced', done => {
    cache.load().subscribe(() => {
      cache.load(true).subscribe(() => {
        expect(fetchCalls).toBe(2);
        done();
      });
    });
  });

  it('refetches after invalidate', done => {
    cache.load().subscribe(() => {
      cache.invalidate();
      cache.load().subscribe(() => {
        expect(fetchCalls).toBe(2);
        done();
      });
    });
  });

  it('records the error message and rethrows', done => {
    response = throwError(() => ({ message: 'boom' }));

    cache.load().subscribe({
      error: () => {
        expect(cache.error()).toBe('boom');
        expect(cache.loading()).toBeFalse();
        done();
      }
    });
  });

  it('falls back to the configured message when the error carries none', done => {
    response = throwError(() => new Error(''));

    cache.load().subscribe({
      error: () => {
        expect(cache.error()).toBe('fallback message');
        done();
      }
    });
  });

  it('add emits a NEW array so OnPush consumers see the change', done => {
    cache.load().subscribe(() => {
      const before = cache.items();
      cache.add(itemB);

      expect(cache.items()).not.toBe(before);
      expect(cache.items()).toEqual([itemA, itemB]);
      done();
    });
  });

  it('replace swaps the matching item and appends when nothing matches', done => {
    cache.load().subscribe(() => {
      cache.replace({ id: 1, name: 'A2' }, item => item.id === 1);
      expect(cache.items()).toEqual([{ id: 1, name: 'A2' }]);

      cache.replace(itemB, item => item.id === 99);
      expect(cache.items()).toEqual([{ id: 1, name: 'A2' }, itemB]);
      done();
    });
  });

  it('remove drops matching items', done => {
    cache.load().subscribe(() => {
      cache.add(itemB);
      cache.remove(item => item.id === 1);

      expect(cache.items()).toEqual([itemB]);
      done();
    });
  });

  it('reset clears items and freshness', done => {
    cache.load().subscribe(() => {
      cache.reset();

      expect(cache.items()).toEqual([]);
      expect(cache.isEmpty()).toBeTrue();

      cache.load().subscribe(() => {
        expect(fetchCalls).toBe(2);
        done();
      });
    });
  });
});
