/**
 * Query Helpers
 * Các function helpers để fetch data với caching
 */

/**
 * Fetch data với caching
 *
 * @param {Array} queryKey - Unique key cho query, ví dụ: ['orders', branchId]
 * @param {Function} queryFn - Function để fetch data từ API
 * @param {Object} options - Options (staleTime, cacheTime)
 * @returns {Promise} Data
 */
async function fetchQuery(queryKey, queryFn, options = {}) {
    // 1. Check cache first
    const cached = queryCache.get(queryKey);

    if (cached && !cached.isStale) {
        console.log('✅ Cache HIT:', queryKey);
        return cached.data;
    }

    // 2. If stale or no cache, fetch from API
    console.log('🔄 Fetching from API:', queryKey);

    try {
        const data = await queryFn();

        // 3. Save to cache
        queryCache.set(queryKey, data, options);

        return data;

    } catch (error) {
        console.error('❌ Fetch error:', queryKey, error);

        // Return stale data if available
        if (cached) {
            console.log('⚠️ Using stale data as fallback');
            return cached.data;
        }

        throw error;
    }
}

/**
 * Invalidate cache (mark as stale)
 */
function invalidateQuery(queryKey) {
    console.log('🔄 Invalidating:', queryKey);
    queryCache.invalidate(queryKey);
}

/**
 * Remove from cache
 */
function removeQuery(queryKey) {
    console.log('🗑️ Removing from cache:', queryKey);
    queryCache.remove(queryKey);
}

/**
 * Clear all cache
 */
function clearAllCache() {
    console.log('🗑️ Clearing all cache');
    queryCache.clear();
}

// Export to window
window.fetchQuery = fetchQuery;
window.invalidateQuery = invalidateQuery;
window.removeQuery = removeQuery;
window.clearAllCache = clearAllCache;

console.log('✅ Query Helpers loaded');