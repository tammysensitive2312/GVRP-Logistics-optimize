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
  @Output() selectionChange = new EventEmitter<OrderDTO[]>
  @Output() editOrder = new EventEmitter<number>();

  displayedColumns: string[] = [
    'select', 'order_code', 'customer_name', 'address', 'priority',
    'time_window', 'demand', 'service_time', 'notes', 'status', 'actions'
  ]

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

  isAllSelected() {
    const numSelected = this.selection.selected.length;
    const numRows = this.dataSource.data.length;
    return numSelected === numRows && numRows > 0;
  }

  toggleAllRows() {
    if (this.isAllSelected()) {
      this.selection.clear();
    } else {
      this.selection.select(...this.dataSource.data);
    }
    this.emitSelection();
  }

  toggleRow(row: OrderDTO) {
    this.selection.toggle(row);
    this.emitSelection();
  }

  private emitSelection() {
    this.selectionChange.emit(this.selection.selected);
  }

  getStatusIcon(status: string): string {
    switch (status) {
      case 'SCHEDULED': return 'schedule';
      case 'ON_ROUTE': return 'local_shipping';
      case 'COMPLETED': return 'check_circle';
      case 'FAILED': return 'cancel';
      default: return 'help';
    }
  }

  getStatusColor(status: string): string {
    switch (status) {
      case 'SCHEDULED': return 'text-blue-500';
      case 'ON_ROUTE': return 'text-orange-500';
      case 'COMPLETED': return 'text-green-500';
      case 'FAILED': return 'text-red-500';
      default: return 'text-gray-500';
    }
  }


}
