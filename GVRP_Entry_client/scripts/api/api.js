/**
 * API Client for VRP System
 * Handles all HTTP requests to backend
 */

// Configuration
const API_BASE_URL = 'http://localhost:8080/api/v1';
const BRANCH_ID = getCurrentBranch();

/**
 * Helper function to get auth headers
 *
 * @param {Object} [customHeaders={}] - Các header bổ sung hoặc ghi đè (Optional).
 * @returns {Object} Đối tượng chứa tất cả các header.
 */
function getHeaders(customHeaders = {}) {
    const authToken = getAuthToken();

    const defaultHeaders = {
        'Content-Type': 'application/json',
        'Authorization': authToken ? `Bearer ${authToken}` : ''
    };

    const finalHeaders = {
        ...defaultHeaders,
        ...customHeaders
    };
    return Object.fromEntries(
        Object.entries(finalHeaders).filter(([key, value]) => value !== undefined && value !== null)
    );
}

// Helper function to handle API errors
function handleApiError(error) {
    console.error('API Error:', error);
    showToast(error.message || 'An error occurred', 'error');
    throw error;
}

// ============================================
// DEPOT API
// ============================================

/**
 * Create a new depot
 * @param {Object} depotData - {name, address, latitude, longitude}
 * @returns {Promise<Object>} Created depot DTO
 */
async function createDepot(depotData) {
    try {
        const response = await fetch(`${API_BASE_URL}/depots`, {
            method: 'POST',
            headers: getHeaders(),
            body: JSON.stringify(depotData)
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to create depot');
        }

        return await response.json();
    } catch (error) {
        return handleApiError(error);
    }
}

/**
 * Get all depots for current branch
 * @returns {Promise<Array>} List of depot DTOs
 */
async function getDepots() {
    const branchId = BRANCH_ID;
    const queryKey = QueryKeys.depots.all(branchId);

    return await fetchQuery(
        queryKey,
        async () => {
            const response = await fetch(`${API_BASE_URL}/depots`, {
                headers: getHeaders()
            });

            if (!response.ok) {
                throw new Error('Failed to fetch depots');
            }

            return await response.json();
        },
        {
            staleTime: 10 * 60 * 1000,  // Fresh for 10 minutes (depots rarely change)
            cacheTime: 30 * 60 * 1000
        }
    );
}

// ============================================
// VEHICLE TYPE API
// ============================================

/**
 * Create a new vehicle type
 * @param {Object} vehicleTypeData
 * @returns {Promise<Object>} Created vehicle type DTO
 */
async function createVehicleType(vehicleTypeData) {
    try {
        const response = await fetch(`${API_BASE_URL}/vehicle-types`, {
            method: 'POST',
            headers: getHeaders(),
            body: JSON.stringify(vehicleTypeData)
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to create vehicle type');
        }

        const result = await response.json();
        invalidateQuery(QueryKeys.vehicleTypes.all(BRANCH_ID));

        return result;
    } catch (error) {
        return handleApiError(error);
    }
}

/**
 * Get all vehicle types for current branch
 * @returns {Promise<Array>} List of vehicle type DTOs
 */
async function getVehicleTypes() {
    const branchId = BRANCH_ID;
    const queryKey = QueryKeys.vehicleTypes.all(branchId);

    return await fetchQuery(
        queryKey,
        async () => {
            const response = await fetch(`${API_BASE_URL}/vehicle-types`, {
                headers: getHeaders()
            });

            if (!response.ok) {
                throw new Error('Failed to fetch vehicle types');
            }

            return await response.json();
        },
        {
            staleTime: 10 * 60 * 1000,
            cacheTime: 30 * 60 * 1000
        }
    );
}

// ============================================
// FLEET API
// ============================================

/**
 * Create a new fleet with vehicles
 * @param {Object} fleetData - {fleetName, vehicles: [...]}
 * @returns {Promise<Object>} Created fleet DTO
 */
async function createFleet(fleetData) {
    try {
        const response = await fetch(`${API_BASE_URL}/fleets`, {
            method: 'POST',
            headers: getHeaders(),
            body: JSON.stringify(fleetData)
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to create fleet');
        }

        const result = await response.json();
        invalidateQuery(QueryKeys.fleets.all(BRANCH_ID));
        invalidateQuery(QueryKeys.vehicles.all(BRANCH_ID));

        return result;
    } catch (error) {
        return handleApiError(error);
    }
}

/**
 * Get fleet by branch ID
 * @returns {Promise<Object>} Fleet DTO with vehicles
 */
async function getFleet() {
    try {
        const response = await fetch(`${API_BASE_URL}/fleets`, {
            headers: getHeaders()
        });

        if (!response.ok) {
            throw new Error('Failed to fetch fleet');
        }

        return await response.json();
    } catch (error) {
        return handleApiError(error);
    }
}

/**
 * Get vehicles by branch ID
 * @returns {Promise<Object>} Vehicle DTO with vehicles
 */
async function getVehicle() {
    const branchId = BRANCH_ID;
    const queryKey = QueryKeys.vehicles.all(branchId);

    return await fetchQuery(
        queryKey,
        async () => {
            const response = await fetch(`${API_BASE_URL}/vehicles`, {
                headers: getHeaders()
            });

            if (!response.ok) {
                throw new Error('Failed to fetch vehicles');
            }

            return await response.json();
        },
        {
            staleTime: 5 * 60 * 1000,  // Fresh for 5 minutes (vehicles don't change often)
            cacheTime: 10 * 60 * 1000
        }
    );
}

// ============================================
// ORDER API
// ============================================

/**
 * Import orders from file or text
 * @param {FormData} formData - Contains file/text, deliveryDate, serviceTime, overwrite
 * @returns {Promise<Object>} Import result
 */
async function importOrders(formData) {
    try {
        const headers = getHeaders({ 'Content-Type': undefined });

        const response = await fetch(`${API_BASE_URL}/orders/import`, {
            method: 'POST',
            headers: headers,
            body: formData
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to import orders');
        }

        const result = await response.json();
        invalidateQuery(QueryKeys.orders.all(BRANCH_ID));

        return result;
    } catch (error) {
        return handleApiError(error);
    }
}

/**
 * Get orders by branch and date
 * @param {string} deliveryDate - Date in YYYY-MM-DD format
 * @returns {Promise<Array>} List of order DTOs
 */
async function getOrders(deliveryDate) {
    const branchId = BRANCH_ID;

    // Define query key
    const queryKey = QueryKeys.orders.byDate(branchId, deliveryDate);

    // Use fetchQuery with cache
    return await fetchQuery(
        queryKey,
        async () => {
            const url = `${API_BASE_URL}/orders?order=desc`;
            const response = await fetch(url, {
                headers: getHeaders()
            });

            if (!response.ok) {
                throw new Error('Failed to fetch orders');
            }

            return await response.json();
        },
        {
            staleTime: 2 * 60 * 1000,  // Fresh for 2 minutes
            cacheTime: 5 * 60 * 1000   // Cache for 5 minutes
        }
    );
}

/**
 * Get single order by ID
 * @param {number} orderId
 * @returns {Promise<Object>} Order DTO
 */
async function getOrderById(orderId) {
    try {
        const response = await fetch(`${API_BASE_URL}/orders/${orderId}`, {
            headers: getHeaders()
        });

        if (!response.ok) {
            throw new Error('Failed to fetch order');
        }

        return await response.json();
    } catch (error) {
        return handleApiError(error);
    }
}

/**
 * Delete order
 * @param {number} orderId
 * @returns {Promise<void>}
 */
async function deleteOrder(orderId) {
    try {
        const response = await fetch(`${API_BASE_URL}/orders/${orderId}`, {
            method: 'DELETE',
            headers: getHeaders()
        });

        if (!response.ok) {
            throw new Error('Failed to delete order');
        }

        const branchId = BRANCH_ID;
        invalidateQuery(QueryKeys.orders.all(branchId));
        removeQuery(QueryKeys.orders.detail(orderId));

        return true;
    } catch (error) {
        return handleApiError(error);
    }
}

/**
 * Update order
 * @param {number} orderId - ID của đơn hàng cần cập nhật
 * @param {Object} updateData - Dữ liệu cập nhật (OrderInputDTO)
 * @param {string} [deliveryDate] - Ngày giao hàng mới (YYYY-MM-DD), tùy chọn
 * @returns {Promise<Object>} - OrderDTO đã được cập nhật từ Server
 */
async function updateOrder(orderId, updateData, deliveryDate = null) {
    let url = `${API_BASE_URL}/orders/${orderId}`;

    if (deliveryDate) {
        url += `?delivery_date=${deliveryDate}`;
    }

    try {
        const response = await fetch(url, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                ...getHeaders()
            },
            body: JSON.stringify(updateData)
        });

        if (!response.ok) {
            const errorBody = await response.json().catch(() => ({ message: 'Unknown error' }));
            throw new Error(`Failed to update order: ${errorBody.message || response.statusText}`);
        }

        const result = await response.json();

        const branchId = BRANCH_ID;
        invalidateQuery(QueryKeys.orders.all(branchId));
        invalidateQuery(QueryKeys.orders.detail(orderId));

        return result;
    } catch (error) {
        console.error('API Error during order update:', error);
        handleApiError(error);
        throw error;
    }
}

// ============================================
// SOLUTION API (for future use)
// ============================================

/**
 * Get available vehicles for planning
 * @returns {Promise<Array>} List of available vehicle DTOs
 */
async function getAvailableVehicles() {
    try {
        const response = await fetch(`${API_BASE_URL}/solutions/available-vehicles?branchId=${BRANCH_ID}`, {
            headers: getHeaders()
        });

        if (!response.ok) {
            throw new Error('Failed to fetch available vehicles');
        }

        return await response.json();
    } catch (error) {
        return handleApiError(error);
    }
}

window.createDepot = createDepot;
window.getDepots = getDepots;
window.createFleet = createFleet;
window.getFleet = getFleet;
window.importOrders = importOrders;
window.getOrders = getOrders;
window.getOrderById = getOrderById;
window.deleteOrder = deleteOrder;
window.getAvailableVehicles = getAvailableVehicles;
window.updateOrder = updateOrder;
window.createVehicleType = createVehicleType;
window.getVehicleTypes = getVehicleTypes;

console.log('API Client initialized.');