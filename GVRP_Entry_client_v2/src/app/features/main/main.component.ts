import { Component, OnInit, OnDestroy, inject, ViewChild } from '@angular/core';
import {forkJoin, Subject} from 'rxjs';
import {finalize, takeUntil} from 'rxjs/operators';

import {DepotDTO, OrderDTO, VehicleDTO, Stats, OrderFilter} from '@core/models';
import {SidebarComponent} from '@features/main/components/sidebar/sidebar.component';
import {MapComponent} from '@shared/components/map/map.component';
import {ResizableDividerDirective} from '@shared/directives/resizable-divider.directive';
import {OrdersSectionComponent} from '@features/main/orders/orders-section-view/orders-section.component';
import {ToastService} from '@shared/services/toast.service';
import {ApiService} from '@core/services/api.service';
import {MapService} from '@shared/services/map.service';
import {SolutionStore} from '@core/services/solution.store';

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
    OrdersSectionComponent,
    ResizableDividerDirective
  ],
  styleUrls: ['./main.component.scss']
})
export class MainComponent implements OnInit, OnDestroy {
  // Data
  depots: DepotDTO[] = [];
  vehicles: VehicleDTO[] = [];
  orders: OrderDTO[] = [];

  /**
   * Read from SolutionStore instead of a local field: the previous local
   * `solution` was never assigned, so `<app-map [solution]>` stayed null and no
   * optimized route was ever drawn on the map.
   */
  private solutionStore = inject(SolutionStore);
  readonly solution = this.solutionStore.solution;

  isSidebarCollapsed = false;

  @ViewChild(OrdersSectionComponent) private ordersSection?: OrdersSectionComponent;

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

  private currentFilter: OrderFilter = {
    date: new Date().toISOString().split('T')[0]
  };

  private destroy$ = new Subject<void>();

  constructor(
    private apiService: ApiService,
    private toast: ToastService,
    private map: MapService
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
          this.toast.error('Failed to load dashboard data');
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

  /** Resizing lives in ResizableDividerDirective now; the map just re-measures. */
  onMapSectionResized(): void {
    this.map.invalidateSize();
  }

  onDepotClick(depot: DepotDTO): void {
    if (!depot.latitude || !depot.longitude) {
      this.toast.error('The depot does not yet have coordinates.');
      return;
    }
    this.map.centerTo(depot.latitude, depot.longitude, 15);
  }

  onOrderClick(order: OrderDTO): void {
    this.map.highlightOrder(order.id);
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
    this.map.highlightOrder(orderId);
  }

  /** "Edit" inside a map order popup (V1 called EditOrderModal.open directly). */
  onOrderEditRequested(orderId: number): void {
    this.ordersSection?.editOrderById(orderId);
  }
}
