/**
 * Orders Table Component
 * Renders and manages the orders data table
 */

import { AppState } from '../../core/state.js';
import { DOMHelpers } from '../../utils/dom-helpers.js';

export class OrdersTable {
    static #tbody = null;
    static #onRowClick = null;

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

        this.#updateFooter(ordersToRender.length);
    }

    /**
     * Create order row element
     * @private
     */
    static #createOrderRow(order) {
        const row = DOMHelpers.createElement('tr');
        row.id = `order-row-${order.id}`;

        // Row click handler
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
      <td>${this.#escapeHtml(order.service_time)} phút</td>
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
        <td colspan="10">
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
    static #updateFooter(showing) {
        DOMHelpers.setText('footer-showing', showing);
        DOMHelpers.setText('footer-total', AppState.allOrders.length);
        DOMHelpers.setText('footer-selected', AppState.selectedOrdersCount);
    }

    /**
     * Highlight specific row
     * @param {number} orderId
     */
    static highlightRow(orderId) {
        // Remove previous highlights
        document.querySelectorAll('.data-table tbody tr').forEach(row => {
            row.classList.remove('selected');
        });

        // Add highlight to target row
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
    static toggleSelection(orderId, checked) {
        if (checked) {
            AppState.selectOrder(orderId);
        } else {
            AppState.deselectOrder(orderId);
        }

        // Update Plan Routes button
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

        // Update footer
        DOMHelpers.setText('footer-selected', AppState.selectedOrdersCount);
    }

    /**
     * Select all visible orders
     */
    static selectAll() {
        AppState.selectAllOrders();
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
    static toggleSelectAll() {
        const selectAllCheckbox = document.getElementById('select-all');

        if (selectAllCheckbox?.checked) {
            this.selectAll();
        } else {
            this.deselectAll();
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
     * Get selected order IDs
     * @returns {Array<number>}
     */
    static getSelectedOrderIds() {
        return Array.from(AppState.selectedOrders);
    }

    /**
     * Get all visible order IDs
     * @returns {Array<number>}
     */
    static getVisibleOrderIds() {
        return AppState.filteredOrders.map(o => o.id);
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