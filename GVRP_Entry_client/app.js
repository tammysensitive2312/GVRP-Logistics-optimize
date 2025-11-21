/**
 * VRP System - Main Application Logic
 * Manages screens, forms, and business logic
 */

// ============================================
// IMPORTS (Phase 1)
// ============================================
import { Toast } from './utils/toast.js';
import { Loading } from './utils/loading.js';
import { Validator } from './utils/validation.js';
import { DOMHelpers } from './utils/dom-helpers.js';

// ============================================
// APPLICATION STATE
// ============================================
let currentScreen = 'screen-depot-setup';
let vehicleCount = 0;
let selectedOrders = new Set();
let allOrders = [];
let filteredOrders = [];
let availableDepots = [];

// ============================================
// INITIALIZATION
// ============================================

document.addEventListener('DOMContentLoaded', function() {
    console.log('VRP System initializing...');

    // Check authentication
    if (!requireAuth()) {
        return;
    }

    // Display user info in navbar
    displayUserInfo();

    // Check if setup is complete
    checkSetupStatus();

    // Initialize event listeners
    initEventListeners();

    console.log('VRP System ready!');
});

/**
 * Display user info in navbar
 */
function displayUserInfo() {
    const user = getCurrentUser();
    const branch = getCurrentBranch();

    if (user && branch) {
        // Update brand name with branch
        const brandName = document.querySelector('.brand-name');
        if (brandName) {
            brandName.textContent = `VRP System™ - ${branch.name}`;
        }

        // Update user info
        const userName = document.querySelector('.user-name');
        if (userName) {
            userName.textContent = user.fullName || user.username;
        }
    }
}

/**
 * Handle logout
 */
function handleLogout() {
    if (confirm('Bạn có chắc chắn muốn đăng xuất?')) {
        logout();
    }
}

/**
 * Check if user has completed setup (Depot + Fleet)
 */
async function checkSetupStatus() {
    try {
        const depots = await getDepots();
        const fleet = await getFleet();

        if (depots && depots.length > 0 && fleet && fleet.vehicleCount > 0) {
            // Setup complete - go to main screen
            goToScreen('screen-main');
            loadMainScreenData();
        } else {
            // Setup not complete - start from depot setup
            goToScreen('screen-depot-setup');
            initDepotSetupMap();
        }
    } catch (error) {
        console.error('Setup check failed:', error);
        goToScreen('screen-depot-setup');
        initDepotSetupMap();
    }
}

/**
 * Initialize all event listeners
 */
function initEventListeners() {
    // Depot form
    const depotForm = document.getElementById('depot-form');
    if (depotForm) {
        depotForm.addEventListener('submit', handleDepotSubmit);
    }

    // Fleet form
    const fleetForm = document.getElementById('fleet-form');
    if (fleetForm) {
        fleetForm.addEventListener('submit', handleFleetSubmit);
    }

    // File drop zone
    const dropZone = document.getElementById('file-drop-zone');
    if (dropZone) {
        dropZone.addEventListener('click', () => {
            document.getElementById('import-file').click();
        });

        dropZone.addEventListener('dragover', (e) => {
            e.preventDefault();
            dropZone.style.borderColor = '#4A90E2';
        });

        dropZone.addEventListener('dragleave', () => {
            dropZone.style.borderColor = '#E5E5E5';
        });

        dropZone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropZone.style.borderColor = '#E5E5E5';
            const files = e.dataTransfer.files;
            if (files.length > 0) {
                document.getElementById('import-file').files = files;
                handleFileSelect({ target: { files } });
            }
        });
    }

    // Resizable divider for Map/Table split
    initResizableDivider();
}

// ============================================
// RESIZABLE DIVIDER (Map ↔ Table)
// ============================================

let isDragging = false;
let startY = 0;
let startMapHeight = 0;

/**
 * Initialize resizable divider
 */
function initResizableDivider() {
    const divider = document.getElementById('resize-divider');
    const mapSection = document.getElementById('map-section');
    const mainContent = document.querySelector('.main-content');

    if (!divider || !mapSection) return;

    divider.addEventListener('mousedown', function(e) {
        isDragging = true;
        startY = e.clientY;
        startMapHeight = mapSection.offsetHeight;

        divider.classList.add('dragging');
        document.body.style.cursor = 'row-resize';
        document.body.style.userSelect = 'none';

        e.preventDefault();
    });

    document.addEventListener('mousemove', function(e) {
        if (!isDragging) return;

        const deltaY = e.clientY - startY;
        const newMapHeight = startMapHeight + deltaY;

        const mainHeight = mainContent.offsetHeight;
        const minHeight = 200;
        const maxHeight = mainHeight - 200;

        if (newMapHeight >= minHeight && newMapHeight <= maxHeight) {
            mapSection.style.height = newMapHeight + 'px';

            if (mainMap) {
                setTimeout(() => mainMap.invalidateSize(), 10);
            }
        }
    });

    document.addEventListener('mouseup', function() {
        if (isDragging) {
            isDragging = false;
            divider.classList.remove('dragging');
            document.body.style.cursor = '';
            document.body.style.userSelect = '';
        }
    });
}

// ============================================
// SCREEN MANAGEMENT
// ============================================

/**
 * Navigate to a screen
 */
function goToScreen(screenId) {
    // Hide all screens
    document.querySelectorAll('.screen').forEach(screen => {
        screen.classList.remove('active');
    });

    // Show target screen
    const targetScreen = document.getElementById(screenId);
    if (targetScreen) {
        targetScreen.classList.add('active');
        currentScreen = screenId;
        onScreenActivated(screenId);
    }
}

/**
 * Handle screen activation
 */
function onScreenActivated(screenId) {
    switch(screenId) {
        case 'screen-depot-setup':
            setTimeout(() => initDepotSetupMap(), 100);
            break;
        case 'screen-fleet-setup':
            loadDepotsForFleet();
            break;
        case 'screen-main':
            setTimeout(() => {
                initMainMap();
                loadMainScreenData();
            }, 100);
            break;
    }
}

// ============================================
// SCREEN 1: DEPOT SETUP
// ============================================

/**
 * Handle depot form submission
 */
async function handleDepotSubmit(event) {
    event.preventDefault();

    const formData = {
        name: DOMHelpers.getValue('depot-name').trim(),
        address: DOMHelpers.getValue('depot-address').trim(),
        latitude: parseFloat(DOMHelpers.getValue('depot-lat')),
        longitude: parseFloat(DOMHelpers.getValue('depot-lng'))
    };

    // Validation using Validator
    const validation = Validator.validateDepot(formData);
    if (!validation.isValid) {
        Toast.error(validation.errors[0]);
        return;
    }

    Loading.show();

    try {
        await createDepot(formData);
        Toast.success('Depot đã được tạo thành công!');
        goToScreen('screen-fleet-setup');
    } catch (error) {
        console.error('Failed to create depot:', error);
        Toast.error('Không thể tạo depot. Vui lòng thử lại.');
    } finally {
        Loading.hide();
    }
}

/**
 * Reset depot form
 */
function resetDepotForm() {
    DOMHelpers.resetForm('depot-form');
    if (depotMarker && depotSetupMap) {
        depotSetupMap.removeLayer(depotMarker);
        depotMarker = null;
    }
}

// ============================================
// SCREEN 2: FLEET SETUP
// ============================================

/**
 * Load available depots for fleet setup
 */
async function loadDepotsForFleet() {
    Loading.show();

    try {
        availableDepots = await getDepots();

        if (!availableDepots || availableDepots.length === 0) {
            Toast.error('Không tìm thấy depot nào. Vui lòng tạo depot trước.');
            goToScreen('screen-depot-setup');
            return;
        }

        displayDepotInfo();

        if (vehicleCount === 0) {
            addVehicle();
        }

    } catch (error) {
        console.error('Failed to load depots:', error);
        Toast.error('Không thể tải danh sách depot');
    } finally {
        Loading.hide();
    }
}

/**
 * Display depot info in fleet setup
 */
function displayDepotInfo() {
    const infoBox = document.getElementById('depots-info');
    const listContainer = document.getElementById('depots-list-info');

    if (!availableDepots || availableDepots.length === 0) {
        infoBox.style.display = 'none';
        return;
    }

    infoBox.style.display = 'block';
    DOMHelpers.clearChildren('depots-list-info');

    availableDepots.forEach(depot => {
        const item = DOMHelpers.createElement('div',
            { class: 'depot-info-item', html: true },
            `<strong>${depot.name}</strong> - ${depot.address}`
        );
        DOMHelpers.appendChild('depots-list-info', item);
    });
}

/**
 * Build depot options HTML
 */
function buildDepotOptions(selectedId = '') {
    if (!availableDepots || availableDepots.length === 0) {
        return '<option value="">No depots available</option>';
    }

    let html = '<option value="">-- Chọn depot --</option>';

    availableDepots.forEach(depot => {
        const selected = depot.id == selectedId ? 'selected' : '';
        html += `<option value="${depot.id}" ${selected}>${depot.name}</option>`;
    });

    return html;
}

/**
 * Add a new vehicle form
 */
function addVehicle() {
    vehicleCount++;

    const container = document.getElementById('vehicles-container');
    const vehicleCard = document.createElement('div');
    vehicleCard.className = 'vehicle-card';
    vehicleCard.id = `vehicle-${vehicleCount}`;

    vehicleCard.innerHTML = `
        <div class="vehicle-header">
            <span class="vehicle-number">Xe #${vehicleCount}</span>
            <button type="button" class="btn-remove-vehicle" onclick="removeVehicle(${vehicleCount})">
                ✕ Xóa
            </button>
        </div>
        <div class="vehicle-fields">
            <div class="form-group">
                <label>Biển số <span class="required">*</span></label>
                <input type="text" name="vehicleLicensePlate" placeholder="29A-12345" required />
            </div>
            <div class="form-group">
                <label>Loại xe</label>
                <input type="text" name="vehicleFeature" placeholder="Xe tải nhỏ" />
            </div>
            <div class="form-group">
                <label>Tải trọng (kg) <span class="required">*</span></label>
                <input type="number" name="capacity" placeholder="500" min="1" required oninput="updateFleetSummary()" />
            </div>
            <div class="form-group">
                <label>Chi phí cố định (VND)</label>
                <input type="number" name="fixedCost" placeholder="100000" min="0" step="1000" />
            </div>
            <div class="form-group">
                <label>Chi phí/km (VND)</label>
                <input type="number" name="costPerKm" placeholder="5000" min="0" step="100" />
            </div>
            <div class="form-group">
                <label>Chi phí/giờ (VND)</label>
                <input type="number" name="costPerHour" placeholder="50000" min="0" step="1000" />
            </div>
            <div class="form-group">
                <label>Quãng đường tối đa (km)</label>
                <input type="number" name="maxDistance" placeholder="100" min="0" step="1" />
            </div>
            <div class="form-group">
                <label>Thời gian tối đa (giờ)</label>
                <input type="number" name="maxDuration" placeholder="8" min="0" step="0.5" />
            </div>
            <div class="form-group">
                <label>Điểm xuất phát <span class="required">*</span></label>
                <select name="startDepotId" required>
                    ${buildDepotOptions()}
                </select>
            </div>
            <div class="form-group">
                <label>Điểm kết thúc <span class="required">*</span></label>
                <select name="endDepotId" required>
                    ${buildDepotOptions()}
                </select>
            </div>
        </div>
    `;

    container.appendChild(vehicleCard);
    updateFleetSummary();
}

/**
 * Remove a vehicle form
 */
function removeVehicle(id) {
    if (vehicleCount <= 1) {
        Toast.error('Phải có ít nhất 1 xe');
        return;
    }

    DOMHelpers.removeElement(`vehicle-${id}`);
    vehicleCount--;
    updateFleetSummary();
}

/**
 * Update fleet summary
 */
function updateFleetSummary() {
    const vehicles = document.querySelectorAll('.vehicle-card');
    let totalCapacity = 0;

    vehicles.forEach(vehicle => {
        const capacityInput = vehicle.querySelector('input[name="capacity"]');
        if (capacityInput && capacityInput.value) {
            totalCapacity += parseInt(capacityInput.value) || 0;
        }
    });

    DOMHelpers.setText('total-vehicles', vehicles.length);
    DOMHelpers.setText('total-capacity', totalCapacity + ' kg');
}

/**
 * Handle fleet form submission
 */
async function handleFleetSubmit(event) {
    event.preventDefault();

    const fleetName = DOMHelpers.getValue('fleet-name').trim();

    if (!fleetName) {
        Toast.error('Vui lòng nhập tên đội xe');
        return;
    }

    // Collect vehicle data
    const vehicles = [];
    const vehicleCards = document.querySelectorAll('.vehicle-card');

    for (const card of vehicleCards) {
        const startDepotId = parseInt(card.querySelector('select[name="startDepotId"]').value);
        const endDepotId = parseInt(card.querySelector('select[name="endDepotId"]').value);

        if (!startDepotId || !endDepotId) {
            Toast.error('Vui lòng chọn điểm xuất phát và điểm kết thúc cho tất cả xe');
            return;
        }

        const vehicle = {
            vehicle_license_plate: card.querySelector('input[name="vehicleLicensePlate"]').value.trim(),
            vehicle_feature: card.querySelector('input[name="vehicleFeature"]').value.trim(),
            capacity: parseInt(card.querySelector('input[name="capacity"]').value) || 0,
            fixed_cost: parseFloat(card.querySelector('input[name="fixedCost"]').value) || 0,
            cost_per_km: parseFloat(card.querySelector('input[name="costPerKm"]').value) || 0,
            cost_per_hour: parseFloat(card.querySelector('input[name="costPerHour"]').value) || 0,
            max_distance: parseFloat(card.querySelector('input[name="maxDistance"]').value) || null,
            max_duration: parseFloat(card.querySelector('input[name="maxDuration"]').value) || null,
            start_depot_id: startDepotId,
            end_depot_id: endDepotId
        };

        vehicles.push(vehicle);
    }

    const fleetData = {
        fleet_name: fleetName,
        vehicles
    };

    // Validation using Validator
    const validation = Validator.validateFleet(fleetData);
    if (!validation.isValid) {
        Toast.error(validation.errors[0]);
        return;
    }

    Loading.show();

    try {
        await createFleet(fleetData);
        Toast.success('Đội xe đã được tạo thành công!');
        goToScreen('screen-main');
    } catch (error) {
        console.error('Failed to create fleet:', error);
        Toast.error('Không thể tạo đội xe. Vui lòng thử lại.');
    } finally {
        Loading.hide();
    }
}

// ============================================
// SCREEN 3: MAIN SCREEN
// ============================================

/**
 * Load data for main screen
 */
async function loadMainScreenData() {
    Loading.show();

    try {
        const depots = await getDepots();
        if (depots && depots.length > 0) {
            loadDepotMarkers(depots);
            updateDepotsList(depots);
        }

        const today = new Date().toISOString().split('T')[0];
        DOMHelpers.setValue('filter-date', today);
        await loadOrders();

    } catch (error) {
        console.error('Failed to load main screen data:', error);
    } finally {
        Loading.hide();
    }
}

/**
 * Update depots list in sidebar
 */
function updateDepotsList(depots) {
    const container = document.getElementById('depots-list');
    DOMHelpers.clearChildren('depots-list');

    depots.forEach(depot => {
        const depotItem = document.createElement('div');
        depotItem.className = 'depot-item';
        depotItem.innerHTML = `
            <div class="depot-header">
                <span class="depot-icon">📍</span>
                <span class="depot-name">${depot.name}</span>
            </div>
            <div class="depot-details">
                <div class="depot-address">${depot.address}</div>
                <div class="depot-coords">Lat: ${depot.latitude.toFixed(6)}, Lng: ${depot.longitude.toFixed(6)}</div>
                <button class="btn btn-sm btn-text" onclick="centerMapToDepot(${depot.latitude}, ${depot.longitude})">
                    📍 View on Map
                </button>
            </div>
        `;
        container.appendChild(depotItem);
    });
}

/**
 * Load orders from API
 */
async function loadOrders() {
    const date = DOMHelpers.getValue('filter-date');

    Loading.show();

    try {
        allOrders = await getOrders(date);
        filteredOrders = [...allOrders];

        loadOrderMarkers(filteredOrders);
        updateOrdersTable(filteredOrders);
        updateStats(allOrders);

    } catch (error) {
        console.error('Failed to load orders:', error);
        allOrders = [];
        filteredOrders = [];
        updateOrdersTable([]);
    } finally {
        Loading.hide();
    }
}

/**
 * Update orders table
 */
function updateOrdersTable(orders) {
    const tbody = document.getElementById('orders-tbody');
    DOMHelpers.clearChildren('orders-tbody');

    if (orders.length === 0) {
        tbody.innerHTML = `
            <tr class="empty-state">
                <td colspan="6">
                    <div class="empty-content">
                        <div class="empty-icon">📦</div>
                        <div class="empty-text">There are no orders on the selected date</div>
                        <button class="btn btn-primary" onclick="openImportModal()">
                            📥 Import Orders
                        </button>
                    </div>
                </td>
            </tr>
        `;
        return;
    }

    orders.forEach(order => {
        const row = document.createElement('tr');
        row.id = `order-row-${order.id}`;
        row.onclick = () => highlightMarker(order.id);

        const statusIcon = {
            'SCHEDULED': '⏱️',
            'ON_ROUTE': '🚚',
            'COMPLETED': '✅',
            'FAILED': '❌'
        }[order.status] || '⏱️';

        row.innerHTML = `
            <td><input type="checkbox" onclick="event.stopPropagation(); toggleOrderSelection(${order.id}, this.checked)" ${selectedOrders.has(order.id) ? 'checked' : ''} /></td>
            <td>${order.orderCode}</td>
            <td>${order.customerName}</td>
            <td>${order.address}</td>
            <td>${order.demand} kg</td>
            <td>${statusIcon} ${order.status}</td>
        `;

        tbody.appendChild(row);
    });

    DOMHelpers.setText('footer-showing', orders.length);
    DOMHelpers.setText('footer-total', allOrders.length);
}

/**
 * Update stats cards
 */
function updateStats(orders) {
    const scheduled = orders.filter(o => o.status === 'SCHEDULED').length;
    const completed = orders.filter(o => o.status === 'COMPLETED').length;

    const statsCards = document.querySelectorAll('.stat-card');
    if (statsCards.length >= 3) {
        statsCards[0].querySelector('.stat-number').textContent = scheduled;
        statsCards[1].querySelector('.stat-number').textContent = completed;
        statsCards[2].querySelector('.stat-number').textContent = orders.length;
    }
}

/**
 * Toggle order selection
 */
function toggleOrderSelection(orderId, checked) {
    if (checked) {
        selectedOrders.add(orderId);
    } else {
        selectedOrders.delete(orderId);
    }
    updateSelectionCount();
}

/**
 * Toggle select all orders
 */
function toggleSelectAll() {
    const checked = document.getElementById('select-all').checked;

    filteredOrders.forEach(order => {
        if (checked) {
            selectedOrders.add(order.id);
        } else {
            selectedOrders.delete(order.id);
        }
    });

    updateOrdersTable(filteredOrders);
    updateSelectionCount();
}

/**
 * Update selection count
 */
function updateSelectionCount() {
    const count = selectedOrders.size;

    DOMHelpers.setText('selected-count', count);
    DOMHelpers.setText('footer-selected', count);

    const planButton = document.getElementById('btn-plan-routes');
    planButton.disabled = count === 0;
}

/**
 * Highlight table row
 */
function highlightTableRow(orderId) {
    document.querySelectorAll('.data-table tbody tr').forEach(row => {
        row.classList.remove('selected');
    });

    const row = document.getElementById(`order-row-${orderId}`);
    if (row) {
        row.classList.add('selected');
        DOMHelpers.scrollIntoView(`order-row-${orderId}`);
    }
}

/**
 * Apply filters
 */
function applyFilters() {
    const statusFilter = DOMHelpers.getValue('filter-status');
    const priorityFilter = DOMHelpers.getValue('filter-priority');
    const searchQuery = DOMHelpers.getValue('filter-search').toLowerCase();

    filteredOrders = allOrders.filter(order => {
        if (statusFilter && order.status !== statusFilter) {
            return false;
        }

        if (priorityFilter) {
            if (priorityFilter === 'high' && (!order.priority || order.priority > 3)) return false;
            if (priorityFilter === 'medium' && (!order.priority || order.priority < 4 || order.priority > 6)) return false;
            if (priorityFilter === 'low' && (!order.priority || order.priority < 7)) return false;
        }

        if (searchQuery) {
            const searchableText = `${order.orderCode} ${order.customerName} ${order.address}`.toLowerCase();
            if (!searchableText.includes(searchQuery)) {
                return false;
            }
        }

        return true;
    });

    updateOrdersTable(filteredOrders);
    loadOrderMarkers(filteredOrders);
}

/**
 * Clear all filters
 */
function clearFilters() {
    DOMHelpers.setValue('filter-status', '');
    DOMHelpers.setValue('filter-priority', '');
    DOMHelpers.setValue('filter-search', '');
    applyFilters();
}

/**
 * Filter orders by status
 */
function filterOrdersByStatus(status) {
    if (status === 'ALL') {
        DOMHelpers.setValue('filter-status', '');
    } else {
        DOMHelpers.setValue('filter-status', status);
    }
    applyFilters();
}

/**
 * Toggle sidebar visibility
 */
function toggleSidebar() {
    const sidebar = document.getElementById('main-sidebar');
    DOMHelpers.toggleClass('main-sidebar', 'collapsed');

    const btn = sidebar.querySelector('.btn-collapse');
    if (sidebar.classList.contains('collapsed')) {
        btn.innerHTML = '►';
        btn.title = 'Show Sidebar';
    } else {
        btn.innerHTML = '◄ Hide Sidebar';
        btn.title = 'Hide Sidebar';
    }

    setTimeout(() => {
        if (mainMap) mainMap.invalidateSize();
    }, 300);
}

// ============================================
// SCREEN 4: IMPORT MODAL
// ============================================

/**
 * Open import modal
 */
function openImportModal() {
    DOMHelpers.addClass('modal-import', 'active');

    const today = new Date().toISOString().split('T')[0];
    DOMHelpers.setValue('import-delivery-date', today);
}

/**
 * Close import modal
 */
function closeImportModal() {
    DOMHelpers.removeClass('modal-import', 'active');
    DOMHelpers.resetForm('import-form');
    DOMHelpers.toggle('file-selected', false);
}

/**
 * Toggle import method
 */
function toggleImportMethod() {
    const method = document.querySelector('input[name="import-method"]:checked').value;

    if (method === 'file') {
        DOMHelpers.toggle('import-file-section', true);
        DOMHelpers.toggle('import-text-section', false);
    } else {
        DOMHelpers.toggle('import-file-section', false);
        DOMHelpers.toggle('import-text-section', true);
    }
}

/**
 * Handle file selection
 */
function handleFileSelect(event) {
    const file = event.target.files[0];
    if (!file) return;

    // Validate file using Validator
    const validation = Validator.validateFile(file);
    if (!validation.isValid) {
        Toast.error(validation.error);
        return;
    }

    // Show file info
    document.querySelector('.upload-content').style.display = 'none';
    DOMHelpers.toggle('file-selected', true);
    DOMHelpers.setText('file-name', file.name);
}

/**
 * Remove selected file
 */
function removeFile() {
    DOMHelpers.setValue('import-file', '');
    document.querySelector('.upload-content').style.display = 'block';
    DOMHelpers.toggle('file-selected', false);
}

/**
 * Submit import
 */
async function submitImport() {
    const method = document.querySelector('input[name="import-method"]:checked').value;
    const deliveryDate = DOMHelpers.getValue('import-delivery-date');
    const serviceTime = DOMHelpers.getValue('import-service-time');
    const overwrite = document.getElementById('import-overwrite').checked;

    // Validation using Validator
    const validation = Validator.validateImport({
        method,
        deliveryDate,
        serviceTime,
        file: method === 'file' ? document.getElementById('import-file').files[0] : null,
        textData: method === 'text' ? DOMHelpers.getValue('import-text') : null
    });

    if (!validation.isValid) {
        Toast.error(validation.errors[0]);
        return;
    }

    // Build FormData
    const formData = new FormData();

    if (method === 'file') {
        const file = document.getElementById('import-file').files[0];
        formData.append('file', file);
    } else {
        const textData = DOMHelpers.getValue('import-text').trim();
        formData.append('textData', textData);
    }

    formData.append('deliveryDate', deliveryDate);
    formData.append('serviceTime', serviceTime);
    formData.append('overwriteExisting', overwrite);

    Loading.show('Đang import...');

    try {
        const result = await importOrders(formData);

        if (result.success) {
            Toast.success(`Imported ${result.importedCount} orders successfully!`);
            closeImportModal();
            await loadOrders();
        } else {
            Toast.error(`Imported ${result.importedCount} orders. ${result.errors.length} errors.`);
        }

    } catch (error) {
        console.error('Import failed:', error);
        Toast.error('Import thất bại. Vui lòng thử lại.');
    } finally {
        Loading.hide();
    }
}

/**
 * Download template file
 */
function downloadTemplate() {
    const csvContent = 'orderCode,customerName,customerPhone,address,latitude,longitude,demand,serviceTime,timeWindowStart,timeWindowEnd,priority,deliveryNotes\n' +
        'ORD001,John Doe,0901234567,123 Main St,21.028511,105.804817,50.5,5,08:00,12:00,1,Handle with care\n' +
        'ORD002,Jane Smith,0912345678,456 Second Ave,21.030000,105.810000,30.0,10,09:00,13:00,2,\n';

    const blob = new Blob([csvContent], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'order_template.csv';
    a.click();
    URL.revokeObjectURL(url);
}

// Placeholder functions for incomplete features
function openAddOrderModal() {
    Toast.success('Add Order feature - Coming soon!');
}

function exportOrders() {
    Toast.success('Export feature - Coming soon!');
}

function deleteSelectedOrders() {
    if (selectedOrders.size === 0) {
        Toast.error('No orders selected');
        return;
    }
    Toast.success(`Delete ${selectedOrders.size} orders - Coming soon!`);
}

function bulkEditOrders() {
    Toast.success('Bulk Edit feature - Coming soon!');
}

function viewOrderDetails(orderId) {
    Toast.success(`View order #${orderId} - Coming soon!`);
}

function previousPage() {
    Toast.success('Pagination - Coming soon!');
}

function nextPage() {
    Toast.success('Pagination - Coming soon!');
}

window.resetDepotForm = resetDepotForm;

window.addVehicle = addVehicle;
window.updateFleetSummary = updateFleetSummary;
window.removeVehicle = removeVehicle;

console.log('App.js loaded successfully!');