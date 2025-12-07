/**
 * Simple Query Cache
 * Giống React Query nhưng đơn giản hơn nhiều
 */

class SimpleQueryCache {
    constructor() {
        this.cache = new Map();
        this.listeners = new Set();
    }

    /**
     * Get data from cache
     */
    get(key) {
        const stringKey = JSON.stringify(key);
        const entry = this.cache.get(stringKey);

        if (!entry) return null;

        // Check if expired
        if (Date.now() > entry.expiresAt) {
            this.cache.delete(stringKey);
            return null;
        }

        return {
            data: entry.data,
            isStale: Date.now() > entry.staleAt
        };
    }

    /**
     * Save data to cache
     */
    set(key, data, options = {}) {
        const stringKey = JSON.stringify(key);
        const now = Date.now();

        // Default: fresh for 5 minutes, expire after 10 minutes
        const staleTime = options.staleTime || 5 * 60 * 1000;
        const cacheTime = options.cacheTime || 10 * 60 * 1000;

        this.cache.set(stringKey, {
            data: data,
            fetchedAt: now,
            staleAt: now + staleTime,
            expiresAt: now + cacheTime
        });

        // Notify listeners
        this.notify(key, data);
    }

    /**
     * Remove from cache
     */
    remove(key) {
        const stringKey = JSON.stringify(key);
        this.cache.delete(stringKey);
        this.notify(key, null);
    }

    /**
     * Clear all cache
     */
    clear() {
        this.cache.clear();
        this.notify(null, null);
    }

    /**
     * Subscribe to changes
     */
    subscribe(callback) {
        this.listeners.add(callback);
        return () => this.listeners.delete(callback);
    }

    /**
     * Notify listeners
     */
    notify(key, data) {
        this.listeners.forEach(listener => listener(key, data));
    }

    /**
     * Mark as stale (need refetch)
     */
    invalidate(key) {
        const stringKey = JSON.stringify(key);
        const entry = this.cache.get(stringKey);
        if (entry) {
            entry.staleAt = 0; // Mark as stale immediately
        }
    }
}

// Create global instance
window.queryCache = new SimpleQueryCache();

console.log('✅ Query Cache initialized');
