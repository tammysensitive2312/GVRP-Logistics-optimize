import {Component, inject, Input, OnInit} from '@angular/core';
import {ApiService} from '@core/services/api.service';
import {OrderDTO, OrderFilter, SolutionDTO} from '@core/models';
import {ToastService} from '@shared/services/toast.service';
import {PageEvent} from '@angular/material/paginator';
import {CommonModule} from '@angular/common';
import {MatTabsModule} from '@angular/material/tabs';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {OrdersTableComponent} from '@features/main/orders/components/orders-table/orders-table.component';
import {OrdersFiltersComponent} from '@features/main/orders/components/orders-filters/orders-filters.component';

@Component({
  selector: 'app-orders-section',
  standalone: true,
  imports: [
    CommonModule,
    MatTabsModule,
    MatProgressSpinnerModule,
    OrdersTableComponent,
    OrdersFiltersComponent
  ],
  templateUrl: './orders-section.component.html',
  styleUrl: './orders-section.component.scss'
})
export class OrdersSectionComponent implements OnInit {
  private apiService = inject(ApiService);
  private toastService =  inject(ToastService);

  selectedTabIndex = 0;

  orders: OrderDTO[] = [];
  solution: SolutionDTO | null = null;
  selectedOrders: OrderDTO[] = [];

  totalElements = 0;
  pageSize = 10;
  currentPage = 0;

  currentFilters: OrderFilter = {
    date: new Date().toISOString().split('T')[0]
  };

  isLoading = false;

  ngOnInit() {
    this.loadOrders();
  }

  loadOrders() {
    this.isLoading = true;

    this.apiService.getOrders(this.currentFilters, this.currentPage, this.pageSize)
      .subscribe({
        next: (response) => {
          this.orders = response.content;
          this.totalElements = response.total_elements;
          this.isLoading = false;
        },
        error: (err) => {
          console.error('Error loading order:', err);
          this.isLoading = false;
          this.toastService.error('Error loading order:', err)
        }
      })
  }

  onFilterChange(newFilters: OrderFilter) {
    this.currentFilters = newFilters;
    this.currentPage = 0;
    this.loadOrders();
  }

  onPageChange(even: PageEvent) {
    this.currentPage = even.pageIndex;
    this.pageSize = even.pageSize;
    this.loadOrders();
  }

  onOrderSelectionChange(selected: OrderDTO[]) {
    this.selectedOrders = selected;
    console.log(`${selected.length} orders selected.`);
    // TODO: Bật/tắt nút "Plan Routes" dựa trên selected.length
  }

  onEditOrder(orderId: number) {
    console.log('Open Edit Order Modal with ID: ', orderId);
    // TODO: Mở Dialog (MatDialog)
  }

}
