import {Component, inject, OnInit, OnDestroy, EventEmitter, Output} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTabsModule } from '@angular/material/tabs';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import {Subject} from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { ApiService } from '@core/services/api.service';
import { ToastService } from '@shared/services/toast.service';

import {OrderDTO, OrderFilter, RoutePlanningRequest, SolutionDTO} from '@core/models';
import {SolutionStore} from '@core/services/solution.store';
import {
  BackgroundJobDialogComponent,
  BackgroundJobDialogData,
  BackgroundJobDialogResult
} from '@features/main/jobs/background-job-dialog/background-job-dialog.component';
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
} from '@features/main/jobs/route-planning-dialog/route-planning-dialog.component';
import {
  ImportDialogResult,
  ImportOrdersDialogComponent
} from '@features/main/orders/components/import-orders-dialog/import-orders-dialog.component';
import {
  EditOrderDialogComponent,
  EditOrderDialogData
} from '@features/main/orders/components/edit-orders-dialog/edit-order-dialog.component';
import {JobMonitoringDialogComponent} from '@features/main/jobs/job-monitoring-dialog/job-monitoring-dialog.component';


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
  private dialog = inject(MatDialog);
  private router = inject(Router);

  selectedTabIndex = 0;

  orders: OrderDTO[] = [];
  selectedOrders: OrderDTO[] = [];

  /** Shared with the map and the route/timeline views via SolutionStore. */
  private solutionStore = inject(SolutionStore);
  readonly solution = this.solutionStore.solution;

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

      // Build FormData and call the API
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

      const request: RoutePlanningRequest = {
        order_ids: this.selectedOrders.map(o => o.id),
        vehicle_ids: this.vehicleSelectionService.getSelectedIds(),
        preferences: {
          goal: result.optimizationGoal,
          speed: result.optimizationSpeed,
          allow_unassigned_orders: result.allowUnassigned,
          time_window_mode: result.timeWindowMode,
          enable_pareto_analysis: result.enablePareto
        }
      };

      this.apiService.submitRoutePlanningJob(request)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: (job) => {
            if (result.optimizationSpeed === 'FAST') {
              const monitoringDialog = this.dialog.open(JobMonitoringDialogComponent, {
                width: '560px',
                maxWidth: '90vw',
                disableClose: true,
                data: { job }
              });

              monitoringDialog.afterClosed()
                .pipe(takeUntil(this.destroy$))
                .subscribe((monitorResult) => {
                  if (monitorResult?.solution) {
                    this.setSolution(monitorResult.solution);
                  }
                });

            } else {
              // V1 opened #modal-background-job for NORMAL / HIGH_QUALITY runs.
              this.dialog
                .open<
                  BackgroundJobDialogComponent,
                  BackgroundJobDialogData,
                  BackgroundJobDialogResult
                >(BackgroundJobDialogComponent, {
                  width: '560px',
                  maxWidth: '90vw',
                  data: { job }
                })
                .afterClosed()
                .pipe(takeUntil(this.destroy$))
                .subscribe(action => {
                  if (action === 'view-history') {
                    void this.router.navigate(['/jobs']);
                  }
                });
            }
          },
          error: (err) => {
            this.toastService.error(err.message || 'Failed to submit optimization job');
          }
        });
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

  /** Entry point for the map's order popup "Edit" button. */
  editOrderById(orderId: number): void {
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

  // Removed: restoreAppState().
  // It matched `lastUrl` against 'routes' / 'timeline', substrings this app's
  // URLs never contain, so the tab restore never fired - dead code. The other
  // half restored a solution from localStorage, which is now an explicit action
  // on the /jobs screen instead of hidden state.

  /**
   * Load a solution by id (job monitoring, history, or state restore).
   * `silent` keeps a failed restore quiet, as V1 did on boot.
   */
  loadSolution(solutionId: number, options: { silent?: boolean } = {}): void {
    this.solutionStore
      .loadById(solutionId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.selectedTabIndex = 1;
        },
        error: (err: unknown) => {
          console.error('Failed to load solution:', err);
          if (!options.silent) {
            this.toastService.error('Failed to load solution');
          }
        }
      });
  }

  /**
   * Clear current solution
   */
  clearSolution(): void {
    this.solutionStore.clear();
    this.selectedTabIndex = 0; // Back to Orders tab
  }

  /**
   * Set solution from external source (e.g., job monitoring)
   */
  setSolution(solution: SolutionDTO): void {
    this.solutionStore.setSolution(solution);
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
    return this.solution();
  }
}
