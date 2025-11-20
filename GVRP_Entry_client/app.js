/**
 * VRP System - Main Application Logic
 * Manages screens, forms, and business logic
 */

// Application state
let currentScreen = 'screen-depot-setup';
let vehicleCount = 0;
let selectedOrders = new Set();
let allOrders = [];
let filteredOrders = [];

// ============================================
// INITIALIZATION
// ============================================

document.addEventListener('DOMContentLoaded', function() {
    console.log('VRP System initializing...');

    // Check if setup is complete
    checkSetupStatus();

    // Initialize event listeners
    initEventListeners();

    console.log('VRP System ready!');
});

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
        // On error, start from depot setup
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

    // Import form
    const importForm = document.getElementById('import-form');
    if (importForm) {
        // Form submission handled by submitImport()
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

        // Constraints: min 200px, max 80% of main content
        const mainHeight = mainContent.offsetHeight;
        const minHeight = 200;
        const maxHeight = mainHeight - 200; // Reserve at least 200px for table

        if (newMapHeight >= minHeight && newMapHeight <= maxHeight) {
            mapSection.style.height = newMapHeight + 'px';

            // Invalidate map size to prevent rendering issues
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
 * @param {string} screenId
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

        // Initialize screen-specific logic
        onScreenActivated(screenId);
    }
}

/**
 * Handle screen activation
 * @param {string} screenId
 */
function onScreenActivated(screenId) {
    switch(screenId) {
        case 'screen-depot-setup':
            setTimeout(() => initDepotSetupMap(), 100);
            break;
        case 'screen-fleet-setup':
            // Add first vehicle by default
            if (vehicleCount === 0) {
                addVehicle();
            }
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
        name: document.getElementById('depot-name').value.trim(),
        address: document.getElementById('depot-address').value.trim(),
        latitude: parseFloat(document.getElementById('depot-lat').value),
        longitude: parseFloat(document.getElementById('depot-lng').value)
    };

    // Validation
    if (!formData.name) {
        showToast('Vui lòng nhập tên depot', 'error');
        return;
    }

    if (!formData.latitude || !formData.longitude) {
        showToast('Vui lòng chọn vị trí trên bản đồ', 'error');
        return;
    }

    // Show loading
    showLoading(true);

    try {
        await createDepot(formData);

        // Move to fleet setup
        goToScreen('screen-fleet-setup');
    } catch (error) {
        console.error('Failed to create depot:', error);
    } finally {
        showLoading(false);
    }
}

/**
 * Reset depot form
 */
function resetDepotForm() {
    document.getElementById('depot-form').reset();
    if (depotMarker && depotSetupMap) {
        depotSetupMap.removeLayer(depotMarker);
        depotMarker = null;
    }
}

// ============================================
// SCREEN 2: FLEET SETUP
// ============================================

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
                <input type="number" name="capacity" placeholder="500" min="1" required onchange="updateFleetSummary()" />
            </div>
            <div class="form-group">
                <label>Chi phí cố định</label>
                <input type="number" name="fixedCost" placeholder="100000" min="0" step="1000" />
            </div>
            <div class="form-group">
                <label>Chi phí/km</label>
                <input type="number" name="costPerKm" placeholder="5000" min="0" step="100" />
            </div>
            <div class="form-group">
                <label>Chi phí/giờ</label>
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
        </div>
    `;

    container.appendChild(vehicleCard);
    updateFleetSummary();
}

/**
 * Remove a vehicle form
 * @param {number} id
 */
function removeVehicle(id) {
    if (vehicleCount <= 1) {
        showToast('Phải có ít nhất 1 xe', 'error');
        return;
    }

    const vehicleCard = document.getElementById(`vehicle-${id}`);
    if (vehicleCard) {
        vehicleCard.remove();
        vehicleCount--;
        updateFleetSummary();
    }
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

    document.getElementById('total-vehicles').textContent = vehicles.length;
    document.getElementById('total-capacity').textContent = totalCapacity + ' kg';
}

/**
 * Handle fleet form submission
 */
async function handleFleetSubmit(event) {
    event.preventDefault();

    const fleetName = document.getElementById('fleet-name').value.trim();

    if (!fleetName) {
        showToast('Vui lòng nhập tên đội xe', 'error');
        return;
    }

    // Collect vehicle data
    const vehicles = [];
    const vehicleCards = document.querySelectorAll('.vehicle-card');

    vehicleCards.forEach(card => {
        const vehicle = {
            vehicleLicensePlate: card.querySelector('input[name="vehicleLicensePlate"]').value.trim(),
            vehicleFeature: card.querySelector('input[name="vehicleFeature"]').value.trim(),
            capacity: parseInt(card.querySelector('input[name="capacity"]').value) || 0,
            fixedCost: parseFloat(card.querySelector('input[name="fixedCost"]').value) || 0,
            costPerKm: parseFloat(card.querySelector('input[name="costPerKm"]').value) || 0,
            costPerHour: parseFloat(card.querySelector('input[name="costPerHour"]').value) || 0,
            maxDistance: parseFloat(card.querySelector('input[name="maxDistance"]').value) || null,
            maxDuration: parseFloat(card.querySelector('input[name="maxDuration"]').value) || null
        };

        vehicles.push(vehicle);
    });

    // Validation
    if (vehicles.length === 0) {
        showToast('Vui lòng thêm ít nhất 1 xe', 'error');
        return;
    }

    const fleetData = {
        fleetName,
        vehicles
    };

    showLoading(true);

    try {
        await createFleet(fleetData);

        // Move to main screen
        goToScreen('screen-main');
    } catch (error) {
        console.error('Failed to create fleet:', error);
    } finally {
        showLoading(false);
    }
}

// ============================================
// SCREEN 3: MAIN SCREEN
// ============================================

/**
 * Load data for main screen
 */
async function loadMainScreenData() {
    showLoading(true);

    try {
        // Load depots
        const depots = await getDepots();
        if (depots && depots.length > 0) {
            loadDepotMarkers(depots);
            updateDepotsList(depots);
        }

        // Load orders for today
        const today = new Date().toISOString().split('T')[0];
        document.getElementById('filter-date').value = today;
        await loadOrders();

    } catch (error) {
        console.error('Failed to load main screen data:', error);
    } finally {
        showLoading(false);
    }
}

/**
 * Update depots list in sidebar
 * @param {Array} depots
 */
function updateDepotsList(depots) {
    const container = document.getElementById('depots-list');
    container.innerHTML = '';

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
    const date = document.getElementById('filter-date').value;

    showLoading(true);

    try {
        allOrders = await getOrders(date);
        filteredOrders = [...allOrders];

        // Update map
        loadOrderMarkers(filteredOrders);

        // Update table
        updateOrdersTable(filteredOrders);

        // Update stats
        updateStats(allOrders);

    } catch (error) {
        console.error('Failed to load orders:', error);
        allOrders = [];
        filteredOrders = [];
        updateOrdersTable([]);
    } finally {
        showLoading(false);
    }
}

/**
 * Update orders table
 * @param {Array} orders
 */
function updateOrdersTable(orders) {
    const tbody = document.getElementById('orders-tbody');
    tbody.innerHTML = '';

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

    // Update footer
    document.getElementById('footer-showing').textContent = orders.length;
    document.getElementById('footer-total').textContent = allOrders.length;
}

/**
 * Update stats cards
 * @param {Array} orders
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
 * @param {number} orderId
 * @param {boolean} checked
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

    document.getElementById('selected-count').textContent = count;
    document.getElementById('footer-selected').textContent = count;

    const planButton = document.getElementById('btn-plan-routes');
    planButton.disabled = count === 0;
}

/**
 * Highlight table row
 * @param {number} orderId
 */
function highlightTableRow(orderId) {
    // Remove previous highlights
    document.querySelectorAll('.data-table tbody tr').forEach(row => {
        row.classList.remove('selected');
    });

    // Add highlight
    const row = document.getElementById(`order-row-${orderId}`);
    if (row) {
        row.classList.add('selected');
        row.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
}

/**
 * Apply filters
 */
function applyFilters() {
    const statusFilter = document.getElementById('filter-status').value;
    const priorityFilter = document.getElementById('filter-priority').value;
    const searchQuery = document.getElementById('filter-search').value.toLowerCase();

    filteredOrders = allOrders.filter(order => {
        // Status filter
        if (statusFilter && order.status !== statusFilter) {
            return false;
        }

        // Priority filter
        if (priorityFilter) {
            if (priorityFilter === 'high' && (!order.priority || order.priority > 3)) return false;
            if (priorityFilter === 'medium' && (!order.priority || order.priority < 4 || order.priority > 6)) return false;
            if (priorityFilter === 'low' && (!order.priority || order.priority < 7)) return false;
        }

        // Search filter
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
    document.getElementById('filter-status').value = '';
    document.getElementById('filter-priority').value = '';
    document.getElementById('filter-search').value = '';
    applyFilters();
}

/**
 * Filter orders by status (from stat card)
 * @param {string} status
 */
function filterOrdersByStatus(status) {
    if (status === 'ALL') {
        document.getElementById('filter-status').value = '';
    } else {
        document.getElementById('filter-status').value = status;
    }
    applyFilters();
}

/**
 * Toggle sidebar visibility
 */
function toggleSidebar() {
    const sidebar = document.getElementById('main-sidebar');
    sidebar.classList.toggle('collapsed');

    const btn = sidebar.querySelector('.btn-collapse');
    if (sidebar.classList.contains('collapsed')) {
        btn.innerHTML = '►';
        btn.title = 'Show Sidebar';
    } else {
        btn.innerHTML = '◄ Hide Sidebar';
        btn.title = 'Hide Sidebar';
    }

    // Invalidate map size after animation
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
    const modal = document.getElementById('modal-import');
    modal.classList.add('active');

    // Set default date to today
    const today = new Date().toISOString().split('T')[0];
    document.getElementById('import-delivery-date').value = today;
}

/**
 * Close import modal
 */
function closeImportModal() {
    const modal = document.getElementById('modal-import');
    modal.classList.remove('active');

    // Reset form
    document.getElementById('import-form').reset();
    document.getElementById('file-selected').style.display = 'none';
}

/**
 * Toggle import method
 */
function toggleImportMethod() {
    const method = document.querySelector('input[name="import-method"]:checked').value;

    const fileSection = document.getElementById('import-file-section');
    const textSection = document.getElementById('import-text-section');

    if (method === 'file') {
        fileSection.style.display = 'block';
        textSection.style.display = 'none';
    } else {
        fileSection.style.display = 'none';
        textSection.style.display = 'block';
    }
}

/**
 * Handle file selection
 * @param {Event} event
 */
function handleFileSelect(event) {
    const file = event.target.files[0];
    if (!file) return;

    // Validate file
    const validExtensions = ['.csv', '.xlsx'];
    const fileName = file.name.toLowerCase();
    const isValid = validExtensions.some(ext => fileName.endsWith(ext));

    if (!isValid) {
        showToast('Chỉ chấp nhận file CSV hoặc Excel', 'error');
        return;
    }

    if (file.size > 10 * 1024 * 1024) {
        showToast('File không được vượt quá 10MB', 'error');
        return;
    }

    // Show file info
    document.querySelector('.upload-content').style.display = 'none';
    const fileSelected = document.getElementById('file-selected');
    fileSelected.style.display = 'flex';
    document.getElementById('file-name').textContent = file.name;
}

/**
 * Remove selected file
 */
function removeFile() {
    document.getElementById('import-file').value = '';
    document.querySelector('.upload-content').style.display = 'block';
    document.getElementById('file-selected').style.display = 'none';
}

/**
 * Submit import
 */
async function submitImport() {
    const method = document.querySelector('input[name="import-method"]:checked').value;
    const deliveryDate = document.getElementById('import-delivery-date').value;
    const serviceTime = document.getElementById('import-service-time').value;
    const overwrite = document.getElementById('import-overwrite').checked;

    // Validation
    if (!deliveryDate) {
        showToast('Vui lòng chọn ngày giao hàng', 'error');
        return;
    }

    if (!serviceTime || serviceTime < 0) {
        showToast('Thời gian phục vụ phải >= 0', 'error');
        return;
    }

    // Build FormData
    const formData = new FormData();

    if (method === 'file') {
        const file = document.getElementById('import-file').files[0];
        if (!file) {
            showToast('Vui lòng chọn file', 'error');
            return;
        }
        formData.append('file', file);
    } else {
        const textData = document.getElementById('import-text').value.trim();
        if (!textData) {
            showToast('Vui lòng nhập dữ liệu', 'error');
            return;
        }
        formData.append('textData', textData);
    }

    formData.append('deliveryDate', deliveryDate);
    formData.append('serviceTime', serviceTime);
    formData.append('overwriteExisting', overwrite);

    showLoading(true);

    try {
        const result = await importOrders(formData);

        if (result.success) {
            showToast(`Imported ${result.importedCount} orders successfully!`, 'success');
            closeImportModal();

            // Reload orders
            await loadOrders();
        } else {
            // Handle partial success
            showToast(`Imported ${result.importedCount} orders. ${result.errors.length} errors.`, 'error');
        }

    } catch (error) {
        console.error('Import failed:', error);
    } finally {
        showLoading(false);
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

// ============================================
// UTILITY FUNCTIONS
// ============================================

/**
 * Show loading overlay
 * @param {boolean} show
 */
function showLoading(show) {
    const overlay = document.getElementById('loading-overlay');
    overlay.style.display = show ? 'flex' : 'none';
}

/**
 * Show toast notification
 * @param {string} message
 * @param {string} type - 'success', 'error', or ''
 */
function showToast(message, type = '') {
    const toast = document.getElementById('toast');
    toast.textContent = message;
    toast.className = 'toast show ' + type;

    setTimeout(() => {
        toast.classList.remove('show');
    }, 3000);
}

// Placeholder functions for incomplete features
function openAddOrderModal() {
    showToast('Add Order feature - Coming soon!', 'success');
}

function exportOrders() {
    showToast('Export feature - Coming soon!', 'success');
}

function deleteSelectedOrders() {
    if (selectedOrders.size === 0) {
        showToast('No orders selected', 'error');
        return;
    }
    showToast(`Delete ${selectedOrders.size} orders - Coming soon!`, 'success');
}

function bulkEditOrders() {
    showToast('Bulk Edit feature - Coming soon!', 'success');
}

function viewOrderDetails(orderId) {
    showToast(`View order #${orderId} - Coming soon!`, 'success');
}

function previousPage() {
    showToast('Pagination - Coming soon!', 'success');
}

function nextPage() {
    showToast('Pagination - Coming soon!', 'success');
}

console.log('App.js loaded successfully!');