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
    try {
        const response = await fetch(`${API_BASE_URL}/depots`, {
            headers: getHeaders()
        });

        if (!response.ok) {
            throw new Error('Failed to fetch depots');
        }

        return await response.json();
    } catch (error) {
        return handleApiError(error);
    }
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

        return await response.json();
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
    try {
        const response = await fetch(`${API_BASE_URL}/vehicles`, {
            headers: getHeaders()
        });

        if (!response.ok) {
            throw new Error('Failed to fetch vehicles');
        }

        return await response.json();
    } catch (error) {
        return handleApiError(error);
    }
}

/**
 * Get specific fleet by fleet ID
 * @returns {Promise<Object>} Fleet DTO with vehicles
 */
async function getFleetById(fleet_id) {
    try {
        const response = await fetch(`${API_BASE_URL}/fleets/${fleet_id}`, {
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

        return await response.json();
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
    try {
        const url = `${API_BASE_URL}/orders?order=desc`;
        const response = await fetch(url, {
            headers: getHeaders()
        });

        if (!response.ok) {
            throw new Error('Failed to fetch orders');
        }

        return await response.json();
    } catch (error) {
        return handleApiError(error);
    }
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
 * Update order status
 * @param {number} orderId
 * @param {string} status - OrderStatus enum value
 * @returns {Promise<Object>} Updated order DTO
 */
async function updateOrderStatus(orderId, status) {
    try {
        const response = await fetch(`${API_BASE_URL}/orders/${orderId}/status`, {
            method: 'PATCH',
            headers: getHeaders(),
            body: JSON.stringify({ status })
        });

        if (!response.ok) {
            throw new Error('Failed to update order status');
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

        return true;
    } catch (error) {
        return handleApiError(error);
    }
}



/**
 * Get order by ID
 * @param {number} orderId
 * @returns {Promise<Object>}
 */
async function getOrderById(orderId) {
    try {
        const response = await fetch(`${API_BASE_URL}/orders/${orderId}`, {
            method: 'GET',
            headers: getHeaders()
        });

        if (!response.ok) {
            const errorBody = await response.json().catch(() => ({ message: 'Unknown error' }));
            throw new Error(`Failed to get order: ${errorBody.message || response.statusText}`);
        }

        return await response.json();
    } catch (error) {
        console.error('API Error during get order:', error);
        handleApiError(error);
        throw error;
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

        return await response.json();
    } catch (error) {
        console.error('API Error during order update:', error);
        handleApiError(error);
        throw error;
    }
}

/**
 * Delete order
 * @param {number} orderId
 * @returns {Promise<void>}
 */
// async function deleteOrder(orderId) {
//     try {
//         const response = await fetch(`${API_BASE_URL}/orders/${orderId}`, {
//             method: 'DELETE',
//             headers: getHeaders()
//         });
//
//         if (!response.ok) {
//             const errorBody = await response.json().catch(() => ({ message: 'Unknown error' }));
//             throw new Error(`Failed to delete order: ${errorBody.message || response.statusText}`);
//         }
//
//         return true;
//     } catch (error) {
//         console.error('API Error during delete order:', error);
//         handleApiError(error);
//         throw error;
//     }
// }

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

/**
 * Plan routes (VRP-002)
 * @param {Object} planningRequest - {orderIds: [...], vehicleIds: [...]}
 * @returns {Promise<Object>} Optimization job DTO
 */
async function planRoutes(planningRequest) {
    try {
        const response = await fetch(`${API_BASE_URL}/solutions/plan?branchId=${BRANCH_ID}`, {
            method: 'POST',
            headers: getHeaders(),
            body: JSON.stringify(planningRequest)
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to start route planning');
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
window.updateOrderStatus = updateOrderStatus;
window.deleteOrder = deleteOrder;
window.getAvailableVehicles = getAvailableVehicles;
window.planRoutes = planRoutes;
window.updateOrder = updateOrder;

console.log('API Client initialized.');