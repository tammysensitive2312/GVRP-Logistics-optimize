/**
 * Orders Table with Pagination
 * Complete pagination implementation
 */

import { AppState } from '../../core/state.js';
import { DOMHelpers } from '../../utils/dom-helpers.js';
import { Toast } from '../../utils/toast.js';
import {Loading} from "../../utils/loading.js";

export class OrdersTable {
    static #tbody = null;
    static #onRowClick = null;

    // Pagination state
    static #currentPage = 0;
    static #pageSize = 10;
    static #totalPages = 0;
    static #totalElements = 0;

    /**
     * Initialize orders table
     * @param {Function} onRowClick - Callback when row is clicked
     */
    static init(onRowClick = null) {
        this.#tbody = DOMHelpers.getElement('orders-tbody');

        if (!onRowClick) {
            onRowClick = (orderId) => {
                if (typeof MainMap !== 'undefined') {
                    MainMap.highlightOrder(orderId);
                }
            };
        }
        this.#onRowClick = onRowClick;

        if (!this.#tbody) {
            console.warn('Orders table body not found');
            return;
        }

        // Subscribe to state changes
        AppState.subscribe('filteredOrders', (orders) => {
            this.render(orders);
        });

        AppState.subscribe('selectedOrders', () => {
            this.updateCheckboxes();
        });

        // Initialize page size selector
        this.#initPageSizeSelector();

        // Initialize select all checkbox
        this.#initSelectAllCheckbox();
    }

    /**
     * Initialize select all checkbox
     * @private
     */
    static #initSelectAllCheckbox() {
        const selectAllCheckbox = document.getElementById('select-all');
        if (selectAllCheckbox) {
            selectAllCheckbox.addEventListener('change', (e) => {
                this.toggleSelectAll(e.target.checked);
            });
        }
    }

    /**
     * Initialize page size selector
     * @private
     */
    static #initPageSizeSelector() {
        const selector = document.getElementById('page-size-selector');
        if (selector) {
            selector.addEventListener('change', (e) => {
                this.#pageSize = parseInt(e.target.value);
                this.#currentPage = 0; // Reset to first page
                this.#clearActiveSolution();
                this.loadOrders();
            });
        }
    }

    /**
     * Load orders from API with pagination
     * @param {number} page - Page number (0-indexed)
     * @param {number} size - Page size
     */
    static async loadOrders(page = null, size = null) {
        const pageToLoad = page !== null ? page : this.#currentPage;
        const sizeToLoad = size !== null ? size : this.#pageSize;

        const date = AppState.filters.date;

        if (!date) {
            Toast.error('Please select a date');
            return;
        }

        Loading.show('Loading orders...');

        try {
            // Call API with pagination
            const response = await getOrders(date, pageToLoad, sizeToLoad);

            // Update pagination state
            this.#pageSize = response.page_size;
            this.#totalPages = response.total_pages;
            this.#totalElements = response.total_elements;
            this.#currentPage = response.page_no;

            // Update app state with orders
            AppState.setOrders(response.content || []);

            // Render table
            this.render(AppState.filteredOrders);

            // Update pagination UI
            this.#updatePaginationUI();

            // Update map
            if (typeof MainMap !== 'undefined') {
                MainMap.loadOrders(AppState.filteredOrders);
            }

            // Update sidebar stats
            if (typeof Sidebar !== 'undefined') {
                Sidebar.updateStatsCards();
            }

        } catch (error) {
            console.error('Failed to load orders:', error);
            Toast.error('Failed to load orders');
            AppState.setOrders([]);
            this.render([]);
        } finally {
            Loading.hide();
        }
    }

    /**
     * Go to specific page
     * @param {number} page - Page number (0-indexed)
     */
    static goToPage(page) {
        if (page < 0 || page >= this.#totalPages) {
            return;
        }

        this.#currentPage = page;
        this.#clearActiveSolution();
        this.loadOrders(page);
    }

    /**
     * Go to previous page
     */
    static previousPage() {
        if (this.#currentPage > 0) {
            this.#clearActiveSolution();
            this.goToPage(this.#currentPage - 1);
        }
    }

    /**
     * Go to next page
     */
    static nextPage() {
        if (this.#currentPage < this.#totalPages - 1) {
            this.#clearActiveSolution();
            this.goToPage(this.#currentPage + 1);
        }
    }

    /**
     * Go to first page
     */
    static firstPage() {
        this.goToPage(0);
    }

    /**
     * Go to last page
     */
    static lastPage() {
        this.goToPage(this.#totalPages - 1);
    }

    /**
     * Update pagination UI
     * @private
     */
    static #updatePaginationUI() {
        // Update page info
        DOMHelpers.setText('current-page', this.#currentPage + 1);
        DOMHelpers.setText('total-pages', this.#totalPages);
        DOMHelpers.setText('footer-showing', AppState.filteredOrders.length);
        DOMHelpers.setText('footer-total', this.#totalElements);
        DOMHelpers.setText('footer-selected', AppState.selectedOrdersCount);

        // Update buttons state
        const btnFirst = document.getElementById('btn-first-page');
        const btnPrev = document.getElementById('btn-prev-page');
        const btnNext = document.getElementById('btn-next-page');
        const btnLast = document.getElementById('btn-last-page');

        const isFirstPage = this.#currentPage === 0;
        const isLastPage = this.#currentPage >= this.#totalPages - 1;

        if (btnFirst) btnFirst.disabled = isFirstPage;
        if (btnPrev) btnPrev.disabled = isFirstPage;
        if (btnNext) btnNext.disabled = isLastPage;
        if (btnLast) btnLast.disabled = isLastPage;

        // Render page numbers
        this.#renderPageNumbers();
    }

    /**
     * Render page number buttons
     * @private
     */
    static #renderPageNumbers() {
        const container = document.getElementById('page-numbers');
        if (!container) return;

        container.innerHTML = '';

        // Calculate page range to show (max 5 pages)
        const maxButtons = 5;
        let startPage = Math.max(0, this.#currentPage - Math.floor(maxButtons / 2));
        let endPage = Math.min(this.#totalPages - 1, startPage + maxButtons - 1);

        // Adjust if we're near the end
        if (endPage - startPage < maxButtons - 1) {
            startPage = Math.max(0, endPage - maxButtons + 1);
        }

        // Add "..." before if needed
        if (startPage > 0) {
            const btn = this.#createPageButton(1);
            container.appendChild(btn);

            if (startPage > 1) {
                const dots = document.createElement('span');
                dots.className = 'page-dots';
                dots.textContent = '...';
                container.appendChild(dots);
            }
        }

        // Add page buttons
        for (let i = startPage; i <= endPage; i++) {
            const btn = this.#createPageButton(i + 1);
            container.appendChild(btn);
        }

        // Add "..." after if needed
        if (endPage < this.#totalPages - 1) {
            if (endPage < this.#totalPages - 2) {
                const dots = document.createElement('span');
                dots.className = 'page-dots';
                dots.textContent = '...';
                container.appendChild(dots);
            }

            const btn = this.#createPageButton(this.#totalPages);
            container.appendChild(btn);
        }
    }

    /**
     * Create page button
     * @private
     */
    static #createPageButton(pageNumber) {
        const btn = document.createElement('button');
        btn.className = 'btn-page';
        btn.textContent = pageNumber;

        const pageIndex = pageNumber - 1;

        if (pageIndex === this.#currentPage) {
            btn.classList.add('active');
        }

        btn.onclick = () => this.goToPage(pageIndex);

        return btn;
    }

    /**
     * Render orders table
     * @param {Array} orders - Array of order objects
     */
    static render(orders = null) {
        const ordersToRender = orders || AppState.filteredOrders;

        if (!this.#tbody) {
            console.warn('Table body not initialized');
            return;
        }

        this.#tbody.innerHTML = '';

        if (ordersToRender.length === 0) {
            this.#renderEmptyState();
            return;
        }

        ordersToRender.forEach(order => {
            const row = this.#createOrderRow(order);
            this.#tbody.appendChild(row);
        });

        this.#updateFooter();
        this.updateSelectAllCheckbox();
    }

    /**
     * Create order row element
     * @private
     */
    static #createOrderRow(order) {
        const row = DOMHelpers.createElement('tr');
        row.id = `order-row-${order.id}`;

        row.onclick = () => {
            if (this.#onRowClick) {
                this.#onRowClick(order.id);
            }
            this.highlightRow(order.id);
        };

        const statusIcon = this.#getStatusIcon(order.status);
        const isChecked = AppState.isOrderSelected(order.id);

        const timeWindow = (order.time_window_start && order.time_window_end)
            ? `${order.time_window_start.substring(0, 5)} - ${order.time_window_end.substring(0, 5)}`
            : '—';

        const notes = order.delivery_notes
            ? `<span title="${order.delivery_notes}">${order.delivery_notes.substring(0, 20)}${order.delivery_notes.length > 20 ? '...' : ''}</span>`
            : '—';

        const priorityDisplay = order.priority !== null ? order.priority : '—';

        row.innerHTML = `
            <td>
                <input 
                    type="checkbox" 
                    onclick="event.stopPropagation(); OrdersTable.toggleSelection(${order.id}, this.checked)" 
                    ${isChecked ? 'checked' : ''} 
                />
            </td>
            <td>${this.#escapeHtml(order.order_code)}</td>
            <td>${this.#escapeHtml(order.customer_name)}</td>
            <td>${this.#escapeHtml(order.address)}</td>
            <td>${priorityDisplay}</td>
            <td>${timeWindow}</td>
            <td>${order.demand} kg</td>
            <td>${this.#escapeHtml(order.service_time)} minutes</td>
            <td class="notes-col">${notes}</td>
            <td>${statusIcon} ${order.status}</td>
            <td>
                <button class="btn-icon-sm" 
                        onclick="event.stopPropagation(); EditOrderModal.open(${order.id})" 
                        title="Edit order">
                    ✏️
                </button>
            </td>
        `;

        return row;
    }

    /**
     * Render empty state
     * @private
     */
    static #renderEmptyState() {
        this.#tbody.innerHTML = `
            <tr class="empty-state">
                <td colspan="11">
                    <div class="empty-content">
                        <div class="empty-icon">📦</div>
                        <div class="empty-text">No orders found on the selected date</div>
                        <button class="btn btn-primary" onclick="openImportModal()">
                            📥 Import Orders
                        </button>
                    </div>
                </td>
            </tr>
        `;
    }

    /**
     * Get status icon
     * @private
     */
    static #getStatusIcon(status) {
        const icons = {
            'SCHEDULED': '⏱️',
            'ON_ROUTE': '🚚',
            'COMPLETED': '✅',
            'FAILED': '❌'
        };
        return icons[status] || '⏱️';
    }

    /**
     * Escape HTML to prevent XSS
     * @private
     */
    static #escapeHtml(text) {
        const div = DOMHelpers.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * Update footer info
     * @private
     */
    static #updateFooter() {
        DOMHelpers.setText('footer-showing', AppState.filteredOrders.length);
        DOMHelpers.setText('footer-total', this.#totalElements);
        DOMHelpers.setText('footer-selected', AppState.selectedOrdersCount);
    }

    /**
     * Highlight specific row
     * @param {number} orderId
     */
    static highlightRow(orderId) {
        document.querySelectorAll('.data-table tbody tr').forEach(row => {
            row.classList.remove('selected');
        });

        const row = document.getElementById(`order-row-${orderId}`);
        if (row) {
            row.classList.add('selected');
            row.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
    }

    /**
     * Toggle order selection
     * @param {number} orderId
     * @param {boolean} checked
     */
    static async toggleSelection(orderId, checked) {
        if (checked) {
            AppState.selectOrder(orderId);
        } else {
            AppState.deselectOrder(orderId);
        }

        this.updateCheckboxes();
        this.updatePlanButton();
    }

    /**
     * Update checkboxes based on current selection
     */
    static updateCheckboxes() {
        const rows = this.#tbody?.querySelectorAll('tr');
        if (!rows) return;

        rows.forEach(row => {
            const checkbox = row.querySelector('input[type="checkbox"]');
            if (checkbox) {
                const orderId = parseInt(row.id.replace('order-row-', ''));
                checkbox.checked = AppState.isOrderSelected(orderId);
            }
        });

        this.updateSelectAllCheckbox();
        this.updatePlanButton();
    }

    /**
     * Update Plan Routes button state
     */
    static updatePlanButton() {
        const planButton = document.getElementById('btn-plan-routes');
        const selectedCount = document.getElementById('selected-count');

        if (planButton) {
            planButton.disabled = AppState.selectedOrdersCount === 0;
        }

        if (selectedCount) {
            selectedCount.textContent = AppState.selectedOrdersCount;
        }

        DOMHelpers.setText('footer-selected', AppState.selectedOrdersCount);
    }

    /**
     * Select all visible orders (current page only)
     */
    static selectAllVisible() {
        AppState.filteredOrders.forEach(order => {
            AppState.selectOrder(order.id);
        });
        this.render();
    }

    /**
     * Deselect all orders
     */
    static deselectAll() {
        AppState.deselectAllOrders();
        this.render();
    }

    /**
     * Toggle select all
     */
    static toggleSelectAll(checked) {
        if (checked) {
            // Select all VISIBLE orders on current page
            AppState.filteredOrders.forEach(order => {
                AppState.selectOrder(order.id);
            });
        } else {
            // Deselect all
            AppState.deselectAllOrders();
        }

        this.updateCheckboxes();
        if (typeof updatePlanRoutesButton === 'function') {
            updatePlanRoutesButton();
        }
    }

    /**
     * Select ALL orders in database (not just current page)
     */
    static async selectAllOrdersInDatabase() {
        Loading.show('Loading all orders...');

        try {
            const date = AppState.filters.date;
            if (!date) {
                Toast.error('Please select a date');
                return;
            }

            // Fetch all orders with a large page size
            const response = await getOrders(date, 0, 10000); // Max 10k orders

            if (response.content && response.content.length > 0) {
                response.content.forEach(order => {
                    AppState.selectOrder(order.id);
                });

                Toast.success(`Selected ${response.content.length} orders`);
                this.updateCheckboxes();

                if (typeof updatePlanRoutesButton === 'function') {
                    updatePlanRoutesButton();
                }
            } else {
                Toast.info('No orders found');
            }
        } catch (error) {
            console.error('Failed to load all orders:', error);
            Toast.error('Failed to load all orders');
        } finally {
            Loading.hide();
        }
    }

    /**
     * Clear table
     */
    static clear() {
        if (this.#tbody) {
            this.#tbody.innerHTML = '';
        }
    }

    /**
     * Get pagination info
     */
    static getPaginationInfo() {
        return {
            currentPage: this.#currentPage,
            pageSize: this.#pageSize,
            totalPages: this.#totalPages,
            totalElements: this.#totalElements
        };
    }

    static #clearActiveSolution() {
        AppState.activeSolutionId = null;

        if (typeof MainMap !== 'undefined') {
            if (MainMap.getRouteLayerCount() > 0) {
                const routeLayers = MainMap.getRouteLayers();
                if (routeLayers) {
                    routeLayers.clearLayers();
                }
            }
        }

        if (typeof SolutionDisplay !== 'undefined') {
            SolutionDisplay.clearSolution();
        }

        const routeBtn = document.querySelector('.tab-btn[data-tab="route-tab"]');
        const timelineBtn = document.querySelector('.tab-btn[data-tab="timeline-tab"]');

        if (routeBtn) {
            routeBtn.disabled = true;
            routeBtn.style.opacity = '0.5';
        }
        if (timelineBtn) {
            timelineBtn.disabled = true;
            timelineBtn.style.opacity = '0.5';
        }

        switchContentTab('orders-tab');
    }

    static updateSelectAllCheckbox() {
        const selectAllCheckbox = document.getElementById('select-all');
        if (!selectAllCheckbox) return;

        const visibleOrderIds = this.#tbody?.querySelectorAll('tr').length || 0;
        const selectedVisibleOrders = Array.from(this.#tbody?.querySelectorAll('tr') || [])
            .filter(row => {
                const orderId = parseInt(row.id.replace('order-row-', ''));
                return AppState.isOrderSelected(orderId);
            }).length;

        selectAllCheckbox.checked = visibleOrderIds > 0 && selectedVisibleOrders === visibleOrderIds;
        selectAllCheckbox.indeterminate = selectedVisibleOrders > 0 && selectedVisibleOrders < visibleOrderIds;
    }
}

// Export for global access
if (typeof window !== 'undefined') {
    window.OrdersTable = OrdersTable;
}

// Backward compatibility functions
window.toggleOrderSelection = (orderId, checked) => {
    OrdersTable.toggleSelection(orderId, checked);
};

window.toggleSelectAll = () => {
    OrdersTable.toggleSelectAll();
};

window.highlightTableRow = (orderId) => {
    OrdersTable.highlightRow(orderId);
};

window.previousPage = () => {
    OrdersTable.previousPage();
};

window.nextPage = () => {
    OrdersTable.nextPage();
};

window.firstPage = () => {
    OrdersTable.firstPage();
};

window.lastPage = () => {
    OrdersTable.lastPage();
};