/**
 * Query Keys Convention
 * Định nghĩa tất cả query keys ở đây để dễ quản lý
 */

let QueryKeys = {
    // Orders
    orders: {
        all: (branchId) => ['orders', branchId],
        byStatus: (branchId, status) => ['orders', branchId, status],
        byDate: (branchId, date) => ['orders', branchId, date],
        detail: (orderId) => ['order', orderId]
    },

    // Vehicles
    vehicles: {
        all: (branchId) => ['vehicles', branchId],
        available: (branchId) => ['vehicles', branchId, 'available'],
        detail: (vehicleId) => ['vehicle', vehicleId]
    },

    // Depots
    depots: {
        all: (branchId) => ['depots', branchId],
        detail: (depotId) => ['depot', depotId]
    },

    vehicleTypes: {
        all: (branchId) => ['vehicle-types', branchId],
        detail: (typeId) => ['vehicle-type', typeId]
    },

    // Fleets
    fleets: {
        all: (branchId) => ['fleets', branchId],
        detail: (fleetId) => ['fleet', fleetId]
    }
};

window.QueryKeys = QueryKeys;
console.log('✅ Query Keys loaded');