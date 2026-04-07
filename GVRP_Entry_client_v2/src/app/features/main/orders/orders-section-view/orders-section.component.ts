import {Component, inject, OnInit, OnDestroy, EventEmitter, Output} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTabsModule } from '@angular/material/tabs';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import {Observable, Subject} from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { ApiService } from '@core/services/api.service';
import { ToastService } from '@shared/services/toast.service';
import { StorageService } from '@core/services/storage.service';

import { OrderDTO, OrderFilter, SolutionDTO } from '@core/models';
import { PageEvent } from '@angular/material/paginator';

import { OrdersActionToolbarComponent } from '../components/orders-action-toolbar/orders-action-toolbar.component';
import { OrdersFiltersComponent } from '../components/orders-filters/orders-filters.component';
import { OrdersTableComponent } from '../components/orders-table/orders-table.component';
import {TimelineViewComponent} from '@features/main/orders/timeline-view/timeline-view.component';
import {RouteViewComponent} from '@features/main/orders/route-view/route-view.component';
import {VehicleSelectionService} from '@shared/services/vehicle-selection.service';
import {
  RoutePlanningDialogComponent,
  RoutePlanningDialogData, RoutePlanningDialogResult
} from '@features/main/orders/components/route-planning-dialog/route-planning-dialog.component';
import {
  ImportDialogResult,
  ImportOrdersDialogComponent
} from '@features/main/orders/components/import-orders-dialog/import-orders-dialog.component';
import {
  EditOrderDialogComponent,
  EditOrderDialogData
} from '@features/main/orders/components/edit-orders-dialog/edit-order-dialog.component';


@Component({
  selector: 'app-orders-section-view',
  standalone: true,
  imports: [
    CommonModule,
    MatTabsModule,
    MatProgressSpinnerModule,
    MatIconModule,
    OrdersActionToolbarComponent,
    OrdersFiltersComponent,
    OrdersTableComponent,
    TimelineViewComponent,
    RouteViewComponent,
  ],
  templateUrl: './orders-section.component.html',
  styleUrl: './orders-section.component.scss'
})
export class OrdersSectionComponent implements OnInit, OnDestroy {

  @Output() orderClick = new EventEmitter<OrderDTO>();

  private apiService = inject(ApiService);
  private toastService = inject(ToastService);
  private storageService = inject(StorageService);
  private dialog = inject(MatDialog);

  selectedTabIndex = 0;

  orders: OrderDTO[] = [];
  selectedOrders: OrderDTO[] = [];

  solution: SolutionDTO | null = null;

  totalElements = 0;
  pageSize = 5;
  currentPage = 0;

  currentFilters: OrderFilter = {
    date: new Date().toISOString().split('T')[0]
  };

  isLoading = false;
  selectedVehiclesCount = 0;
  private destroy$ = new Subject<void>();

  constructor(private vehicleSelectionService: VehicleSelectionService) {}

  ngOnInit(): void {
    this.loadOrders();
    this.restoreAppState();
    this.subscribeToVehicleSelection();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadOrders(): void {
    this.isLoading = true;

    this.apiService.getOrders(this.currentFilters, this.currentPage, this.pageSize)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response) => {
          this.orders = response.content;
          this.totalElements = response.total_elements;
          this.isLoading = false;
        },
        error: (err) => {
          console.error('Error loading orders:', err);
          this.toastService.error('Failed to load orders');
          this.isLoading = false;
        }
      });
  }

  onOrderClick(order: OrderDTO): void {
    this.orderClick.emit(order);
  }

  onImportClick(): void {
    const dialogRef = this.dialog.open(ImportOrdersDialogComponent, {
      width: '520px',
      maxWidth: '90vw',
      maxHeight: '90vh'
    });

    dialogRef.afterClosed().subscribe((result: ImportDialogResult | null) => {
      if (!result) return;

      // Build FormData và call API
      const formData = new FormData();
      if (result.method === 'file' && result.file) {
        formData.append('file', result.file);
      } else if (result.method === 'text' && result.textData) {
        formData.append('textData', result.textData);
      }
      formData.append('deliveryDate', result.deliveryDate);
      formData.append('serviceTime', String(result.serviceTime));
      formData.append('overwriteExisting', String(result.overwrite));

      // TODO: this.apiService.importOrders(formData).subscribe(...)
      console.log('Import with:', result);
    });
  }

  onAddClick(): void {
    this.openEditDialog();
  }

  onPlanRoutesClick(): void {
    const totalDemand = this.selectedOrders.reduce((sum, o) => sum + (o.demand || 0), 0);

    const dialogRef = this.dialog.open(RoutePlanningDialogComponent, {
      width: '560px',
      maxWidth: '90vw',
      maxHeight: '90vh',
      data: {
        selectedOrdersCount: this.selectedOrders.length,
        selectedVehiclesCount: this.selectedVehiclesCount,
        totalDemand
      } as RoutePlanningDialogData
    });

    dialogRef.afterClosed().subscribe((result: RoutePlanningDialogResult | null) => {
      if (!result) return;
      // TODO: gọi API optimize với result
      console.log('Start optimization with:', result);
    });
  }

  onDeleteSelected(): void {
    if (this.selectedOrders.length === 0) return;

    const confirmed = confirm(
      `Are you sure you want to delete ${this.selectedOrders.length} selected orders?`
    );

    if (!confirmed) return;

    this.isLoading = true;

    const orderIds = this.selectedOrders.map(o => o.id);

    // TODO: Call API to delete orders
    // this.apiService.deleteOrders(orderIds)
    //   .pipe(takeUntil(this.destroy$))
    //   .subscribe({
    //     next: () => {
    //       this.toastService.success(`Deleted ${orderIds.length} orders`);
    //       this.onClearSelection();
    //       this.loadOrders();
    //     },
    //     error: (err) => {
    //       console.error('Delete failed:', err);
    //       this.toastService.error('Failed to delete orders');
    //       this.isLoading = false;
    //     }
    //   });

    // Mock implementation
    console.log('Deleting orders:', orderIds);
    setTimeout(() => {
      this.toastService.success(`Deleted ${orderIds.length} orders`);
      this.onClearSelection();
      this.loadOrders();
    }, 500);
  }

  onExportClick(): void {
    console.log('Export all orders to CSV');

    // TODO: Implement CSV export
    // this.exportService.exportOrdersToCSV(this.orders);

    this.toastService.info('Export - Coming soon!');
  }

  onAssignToRoute(): void {
    console.log('Assign to route:', this.selectedOrders.length);
    // TODO: Open route assignment dialog
    this.toastService.info('Assign to Route - Coming soon!');
  }

  onBulkUpdateStatus(): void {
    console.log('Bulk update status');
    // TODO: Open status update dialog
    this.toastService.info('Bulk Update Status - Coming soon!');
  }

  onBulkUpdatePriority(): void {
    console.log('Bulk update priority');
    // TODO: Open priority update dialog
    this.toastService.info('Bulk Update Priority - Coming soon!');
  }

  onExportSelected(): void {
    console.log('Export selected orders:', this.selectedOrders.length);

    // TODO: Export selected orders to CSV
    // this.exportService.exportOrdersToCSV(this.selectedOrders);

    this.toastService.info('Export Selected - Coming soon!');
  }

  onClearSelection(): void {
    this.selectedOrders = [];
    // The table component will handle clearing its own selection
    // via the selectionChange event
  }

  onFilterChange(newFilters: OrderFilter): void {
    this.currentFilters = { ...this.currentFilters, ...newFilters };
    this.currentPage = 0; // Reset to first page
    this.loadOrders();
  }

  onPageChange(event: PageEvent): void {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadOrders();
  }

  onOrderSelectionChange(selected: OrderDTO[]): void {
    this.selectedOrders = selected;
    console.log(`${selected.length} orders selected`);
  }

  onEditOrder(orderId: number): void {
    this.openEditDialog(orderId);
  }

  private openEditDialog(orderId?: number): void {
    const dialogRef = this.dialog.open(EditOrderDialogComponent, {
      width: '600px',
      maxWidth: '90vw',
      maxHeight: '90vh',
      data: { orderId } as EditOrderDialogData
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) this.loadOrders();
      }
    )
  }

  /**
   * Subscribe to vehicle selection changes from sidebar
   * This could be from a shared service or state management
   */
  private subscribeToVehicleSelection(): void {
    this.vehicleSelectionService.selectedVehicleIds$
      .pipe(takeUntil(this.destroy$))
      .subscribe(ids => {
        this.selectedVehiclesCount = ids.length;
      });
  }

  /**
   * Restore app state from storage
   */
  private restoreAppState(): void {
    const appState = this.storageService.getAppState();

    if (appState) {
      // Restore selected tab if applicable
      if (appState.lastUrl?.includes('routes')) {
        this.selectedTabIndex = 1;
      } else if (appState.lastUrl?.includes('timeline')) {
        this.selectedTabIndex = 2;
      }

      // Restore active solution if exists
      if (appState.activeSolutionId) {
        // TODO: Load solution by ID
        // this.loadSolution(appState.activeSolutionId);
      }
    }
  }

  /**
   * Load solution by ID (called from job monitoring or history)
   */
  loadSolution(solutionId: number): void {
    this.isLoading = true;

    // TODO: Call API to get solution
    // this.apiService.getSolution(solutionId)
    //   .pipe(takeUntil(this.destroy$))
    //   .subscribe({
    //     next: (solution) => {
    //       this.solution = solution;
    //       this.selectedTabIndex = 1; // Switch to Routes tab
    //       this.isLoading = false;
    //     },
    //     error: (err) => {
    //       console.error('Failed to load solution:', err);
    //       this.toastService.error('Failed to load solution');
    //       this.isLoading = false;
    //     }
    //   });

    console.log('Load solution:', solutionId);
    this.isLoading = false;
  }

  /**
   * Clear current solution
   */
  clearSolution(): void {
    this.solution = null;
    this.selectedTabIndex = 0; // Back to Orders tab
  }

  /**
   * Set solution from external source (e.g., job monitoring)
   */
  setSolution(solution: SolutionDTO): void {
    this.solution = solution;
    this.selectedTabIndex = 1; // Switch to Routes tab
    this.toastService.success('Solution loaded successfully');
  }

  /**
   * Get current selected orders (for external use)
   */
  getSelectedOrders(): OrderDTO[] {
    return this.selectedOrders;
  }

  /**
   * Get current solution (for external use)
   */
  getSolution(): SolutionDTO | null {
    return this.solution;
  }
}
