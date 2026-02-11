import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';
import {forkJoin, Subject} from 'rxjs';
import {finalize, takeUntil} from 'rxjs/operators';

import {DepotDTO, OrderDTO, VehicleDTO, SolutionDTO, Stats, OrderFilter} from '@core/models';
import {SidebarComponent} from '@features/main/components/sidebar/sidebar.component';
import {MapComponent} from '@shared/components/map/map.component';
import {OrdersSectionComponent} from '@features/main/orders/orders-section/orders-section.component';
import {ToastService} from '@shared/services/toast.service';
import {ApiService} from '@core/services/api.service';

interface PaginationState {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Component({
  selector: 'app-main',
  templateUrl: './main.component.html',
  standalone: true,
  imports: [
    SidebarComponent,
    MapComponent,
    OrdersSectionComponent
  ],
  styleUrls: ['./main.component.scss']
})
export class MainComponent implements OnInit, OnDestroy {
  // Data
  depots: DepotDTO[] = [];
  vehicles: VehicleDTO[] = [];
  orders: OrderDTO[] = [];
  solution: SolutionDTO | null = null;

  isSidebarCollapsed = false;

  // Stats
  stats: Stats = {
    scheduled: 0,
    completed: 0,
    total: 0,
    unassigned: 0
  };

  orderPagination: PaginationState = {
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0
  };

  isLoading = true;

  // UI State
  highlightedOrderId: number | null = null;

  // Resize
  private isResizing = false;
  private startY = 0;
  private startHeight = 0;

  private currentFilter: OrderFilter = {
    date: new Date().toISOString().split('T')[0]
  };

  private destroy$ = new Subject<void>();

  constructor(
    private apiService: ApiService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.loadInitialData();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadInitialData(): void {
    this.isLoading = true;

    forkJoin({
      vehicles: this.apiService.getVehicles(),
      depots: this.apiService.getDepots(),
      ordersResponse: this.apiService.getOrders(
        this.currentFilter,
        this.orderPagination.page,
        this.orderPagination.size
      )
    })
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => this.isLoading = false)
      )
      .subscribe({
        next: (response) => {
          this.vehicles = response.vehicles;
          this.depots = response.depots;
          const orderData = response.ordersResponse;
          this.orders = orderData.content || [];
          this.orderPagination = {
            ...this.orderPagination,
            totalElements: orderData.total_elements,
            totalPages: orderData.total_pages
          };
          this.calculateStats();

          console.log('Data loaded. Stats calculated:', this.stats);
        },
        error: (err) => {
          console.error('Error loading data', err);
          this.toast.error('Không thể tải dữ liệu');
        }
      });
  }

  private calculateStats(): void {
    if (!this.orders) return;

    const total = this.orders.length;
    const scheduled = this.orders.filter(o => o.status === 'SCHEDULED').length;
    const completed = this.orders.filter(o => o.status === 'COMPLETED').length;
    const unAssigned = this.orders.filter(o => o.status === 'UNASSIGNED').length;


    this.stats = {
      total: total,
      scheduled: scheduled,
      completed: completed,
      unassigned: unAssigned
    };
  }

  onOrderUpdated(updatedOrders: OrderDTO[]): void {
    this.orders = updatedOrders;
    this.calculateStats();
  }

  // Resize handlers
  onResizeStart(event: MouseEvent): void {
    this.isResizing = true;
    this.startY = event.clientY;

    const mapSection = document.querySelector('.map-section') as HTMLElement;
    if (mapSection) {
      this.startHeight = mapSection.offsetHeight;
    }

    event.preventDefault();
  }

  @HostListener('document:mousemove', ['$event'])
  onResize(event: MouseEvent): void {
    if (!this.isResizing) return;

    const deltaY = event.clientY - this.startY;
    const newHeight = this.startHeight + deltaY;

    const mapSection = document.querySelector('.map-section') as HTMLElement;
    const mainContent = document.querySelector('.main-content') as HTMLElement;

    if (mapSection && mainContent) {
      const mainHeight = mainContent.offsetHeight;
      const minHeight = mainHeight * 0.05;
      const maxHeight = mainHeight * 0.95;

      if (newHeight >= minHeight && newHeight <= maxHeight) {
        mapSection.style.height = `${newHeight}px`;
      }
    }
  }

  @HostListener('document:mouseup')
  onResizeEnd(): void {
    if (this.isResizing) {
      this.isResizing = false;
      // Invalidate map size after resize
      // TODO: Call map.invalidateSize()
    }
  }

  // Event handlers
  onSidebarToggle(): void {
    this.isSidebarCollapsed = !this.isSidebarCollapsed;;

    setTimeout(() => {
      window.dispatchEvent(new Event('resize'))
    })
  }

  onVehicleSelectionChange(selectedIds: number[]): void {
    this.toast.success(`There is ${selectedIds.length} vehicles selected`, 1000)
  }

  onOrderSelectionChange(selectedIds: number[]): void {
    // TODO
  }

  onOrderClicked(orderId: number): void {
    // TODO
  }
}
