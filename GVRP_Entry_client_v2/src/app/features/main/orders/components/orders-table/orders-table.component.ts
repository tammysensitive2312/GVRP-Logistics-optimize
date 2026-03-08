import {Component, EventEmitter, Input, Output, SimpleChanges, ViewChild} from '@angular/core';
import { OrderDTO } from '@core/models';
import {MatPaginator, MatPaginatorModule, PageEvent} from '@angular/material/paginator';
import {MatTableDataSource, MatTableModule} from '@angular/material/table';
import {SelectionModel} from '@angular/cdk/collections';
import {MatChipsModule} from '@angular/material/chips';
import {CommonModule} from '@angular/common';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatIconModule} from '@angular/material/icon';
import {MatButtonModule} from '@angular/material/button';
import {MatTooltipModule} from '@angular/material/tooltip';

@Component({
  selector: 'app-orders-table',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatPaginatorModule,
    MatCheckboxModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
    MatChipsModule
  ],
  templateUrl: './orders-table.component.html',
  styleUrl: './orders-table.component.scss'
})
export class OrdersTableComponent {
  @Input() orders : OrderDTO[] = [];
  @Input() totalElements: number = 0;
  @Input() pageSize: number = 0;
  @Input() currentPage: number = 0;

  @Output() pageChange = new EventEmitter<PageEvent>();
  @Output() selectionChange = new EventEmitter<OrderDTO[]>();
  @Output() editOrder = new EventEmitter<number>();

  pageSizeOptions = [5, 10, 20, 50, 100];

  displayedColumns: string[] = [
    'select', 'order_code', 'customer_name', 'address', 'priority',
    'time_window', 'demand', 'service_time', 'notes', 'status', 'actions'
  ];

  dataSource = new MatTableDataSource<OrderDTO>([]);
  selection = new SelectionModel<OrderDTO>(true, []);

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  ngOnChanges(changes: SimpleChanges) {
    if (changes['orders']) {
      this.dataSource.data = this.orders;
      this.selection.clear();
      this.emitSelection();
    }
  }

  isAllSelected(): boolean {
    const numSelected = this.selection.selected.length;
    const numRows = this.dataSource.data.length;
    return numSelected === numRows && numRows > 0;
  }

  toggleAllRows(): void {
    if (this.isAllSelected()) {
      this.selection.clear();
    } else {
      this.selection.select(...this.dataSource.data);
    }
    this.emitSelection();
  }

  toggleRow(row: OrderDTO): void {
    this.selection.toggle(row);
    this.emitSelection();
  }

  private emitSelection(): void {
    this.selectionChange.emit(this.selection.selected);
  }
  /**
   * Get Material icon name for order status
   */
  getStatusIcon(status: string): string {
    const statusMap: { [key: string]: string } = {
      'UNASSIGNED': 'help_outline',
      'SCHEDULED': 'schedule',
      'ON_ROUTE': 'local_shipping',
      'COMPLETED': 'check_circle',
      'FAILED': 'cancel'
    };
    return statusMap[status] || 'help';
  }

  /**
   * Get CSS class for status badge color
   */
  getStatusColor(status: string): string {
    const colorMap: { [key: string]: string } = {
      'UNASSIGNED': 'text-gray-500',
      'SCHEDULED': 'text-blue-500',
      'ON_ROUTE': 'text-orange-500',
      'COMPLETED': 'text-green-500',
      'FAILED': 'text-red-500'
    };
    return colorMap[status] || 'text-gray-500';
  }

  getStatusClass(status: string): string {
    return `status-${status.toLowerCase()}`;
  }

  getPriorityClass(priority: number | null | undefined): string {
    if (!priority) return 'priority-default';
    return `priority-${priority}`;
  }

  /**
   * Get priority label text
   */
  getPriorityLabel(priority: number | null | undefined): string {
    if (!priority) return '—';

    const labels: { [key: number]: string } = {
      1: 'High',
      2: 'Medium',
      3: 'Low'
    };

    return labels[priority] || String(priority);
  }
  /**
   * Format time window display
   */
  formatTimeWindow(start: string | null, end: string | null): string {
    if (!start || !end) return '—';
    return `${start.slice(0, 5)} - ${end.slice(0, 5)}`;
  }

  /**
   * Format demand with unit
   */
  formatDemand(demand: number): string {
    return `${demand} kg`;
  }

  /**
   * Format service time with unit
   */
  formatServiceTime(time: number): string {
    return `${time} min`;
  }

  /**
   * Truncate text with ellipsis
   */
  truncateText(text: string | null | undefined, maxLength: number = 50): string {
    if (!text) return '—';
    return text.length > maxLength ? text.substring(0, maxLength) + '...' : text;
  }

  /**
   * Handle row click (optional - can be used for row selection)
   */
  onRowClick(row: OrderDTO, event: Event): void {
    // Don't toggle if clicking on action buttons
    const target = event.target as HTMLElement;
    if (target.closest('button') || target.closest('mat-checkbox')) {
      return;
    }

    // Toggle row selection on row click
    this.toggleRow(row);
  }

  /**
   * Handle edit button click
   */
  onEditClick(orderId: number, event: Event): void {
    event.stopPropagation();
    this.editOrder.emit(orderId);
  }

  getShowingFrom(): number {
    return this.totalElements === 0 ? 0 : this.currentPage * this.pageSize + 1;
  }

  getShowingTo(): number {
    return Math.min((this.currentPage + 1) * this.pageSize, this.totalElements);
  }

  getTotalPages(): number {
    return Math.ceil(this.totalElements / this.pageSize) || 1;
  }

  getPageNumbers(): number[] {
    const total = this.getTotalPages();
    const current = this.currentPage;
    const pages: number[] = [];

    let start = Math.max(0, current - 2);
    let end = Math.min(total - 1, start + 4);
    start = Math.max(0, end - 4);

    for (let i = start; i <= end; i++) pages.push(i);
    return pages;
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.getTotalPages()) return;
    this.pageChange.emit({ pageIndex: page, pageSize: this.pageSize, length: this.totalElements });
  }

  onPageSizeChange(event: Event): void {
    const size = Number((event.target as HTMLSelectElement).value);
    this.pageChange.emit({ pageIndex: 0, pageSize: size, length: this.totalElements });
  }

}
