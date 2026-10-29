import {Component, computed, effect, inject, OnInit, OnDestroy, EventEmitter, Output, signal} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTabsModule } from '@angular/material/tabs';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import {Subject} from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { ApiService } from '@core/services/api.service';
import { LezApiService } from '@core/services/lez-api.service';
import { ToastService } from '@shared/services/toast.service';

import {OrderDTO, OrderFilter, RoutePlanningRequest, SolutionDTO} from '@core/models';
import {SolutionComplianceDTO} from '@core/models';
import {BulkStatusResult, BulkStatusRow} from '@core/models';
import {ParetoFrontierDTO} from '@core/models';
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
import {
  CompliancePanelComponent
} from '@features/main/orders/lez-compliance-panel/compliance-panel.component';
import {ParetoViewComponent} from '@features/main/orders/pareto-view/pareto-view.component';
import {VehicleSelectionService} from '@shared/services/vehicle-selection.service';
import {
  BulkStatusDialogComponent,
  BulkStatusDialogData,
  BulkStatusDialogResult
} from '../components/bulk-status-dialog/bulk-status-dialog.component';
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
import { serverErrorMessage } from '@shared/utils/api-error.utils';


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
    CompliancePanelComponent,
    ParetoViewComponent,
  ],
  templateUrl: './orders-section.component.html',
  styleUrl: './orders-section.component.scss'
})
export class OrdersSectionComponent implements OnInit, OnDestroy {

  @Output() orderClick = new EventEmitter<OrderDTO>();

  private apiService = inject(ApiService);
  private lezApi = inject(LezApiService);
  private toastService = inject(ToastService);
  private dialog = inject(MatDialog);
  private router = inject(Router);

  selectedTabIndex = 0;

  orders: OrderDTO[] = [];
  selectedOrders: OrderDTO[] = [];

  /**
   * Ordered low-emission zone names per order id (LEZ-001 P1c, C5). Empty when
   * the badge request failed: a missing badge is not an error to report.
   */
  readonly zoneNamesByOrder = signal<Record<number, string[]>>({});

  /**
   * Compliance of the active solution, owned by the panel and handed to the
   * route view (LEZ-001 P1c, design §3.7). Null means "unknown", never
   * "compliant": the send rule then keeps today's behaviour.
   */
  readonly routeCompliance = signal<SolutionComplianceDTO | null>(null);

  /** Last bulk-update report (null = none/dismissed). */
  readonly bulkResult = signal<BulkStatusResult | null>(null);

  /** Collapsible per-reason breakdown of refused rows (BE-002). */
  readonly bulkDetailsOpen = signal(false);

  /**
   * Failed rows grouped by reason so N identical refusals render as one
   * group instead of N repetitive lines (verified live: 100 identical
   * transition refusals on a 100/page view).
   *
   * Backend reasons embed the order code
   * ("Cannot transition order 'ORD-1' from [SCHEDULED] to [COMPLETED]"),
   * so the grouping key strips per-row identifiers; the full text of the
   * first row is kept for display.
   */
  readonly bulkGroups = computed(() => {
    const failed = this.bulkResult()?.failed ?? [];
    const groups = new Map<string, { reason: string; rows: BulkStatusRow[] }>();
    for (const row of failed) {
      const full = row.reason?.trim() || 'Unknown reason';
      const key = full.replace(/order '[^']*'/g, 'order');
      // Display drops the per-row identifier so the group title reads as
      // the shared pattern, e.g. "Cannot transition from [A] to [B] (100)".
      const display = full.replace(/order '[^']*' /g, '');
      const group = groups.get(key);
      if (group) group.rows.push(row);
      else groups.set(key, { reason: display, rows: [row] });
    }
    return [...groups.values()];
  });

  /** Shared with the map and the route/timeline views via SolutionStore. */
  private solutionStore = inject(SolutionStore);
  readonly solution = this.solutionStore.solution;

  /**
   * Pareto frontier of the active solution's chain (PARETO-002). Loaded lazily;
   * the Pareto tab only appears when candidates exist.
   */
  readonly paretoFrontier = signal<ParetoFrontierDTO | null>(null);
  readonly paretoCandidates = computed(() => this.paretoFrontier()?.candidates ?? []);
  readonly showParetoTab = computed(() => this.paretoCandidates().length > 0);
  /** Solution id whose frontier is already (or last attempted to be) loaded. */
  private paretoLoadedForId: number | null = null;

  totalElements = 0;
  pageSize = 5;
  currentPage = 0;

  currentFilters: OrderFilter = {
    date: new Date().toISOString().split('T')[0]
  };

  isLoading = false;
  selectedVehiclesCount = 0;
  private destroy$ = new Subject<void>();

  constructor(private vehicleSelectionService: VehicleSelectionService) {
    // Refetch the frontier whenever the active solution changes. Deferred past
    // the effect context so signal writes in the subscribe callback do not hit
    // NG0600 when the source resolves synchronously (same pattern as RouteView).
    effect(() => {
      this.solution();
      queueMicrotask(() => this.loadParetoFrontier());
    });
  }

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
          this.loadOrderZones(this.orders);
        },
        error: (err) => {
          console.error('Error loading orders:', err);
          this.toastService.error('Failed to load orders');
          this.isLoading = false;
        }
      });
  }

  /**
   * "LEZ: <zone>" badge data for the visible page (LEZ-001 P1c, C5).
   * One request per page; a failure leaves the table unchanged and is silent,
   * because the badge is an aid, not a gate.
   */
  private loadOrderZones(orders: OrderDTO[]): void {
    const ids = orders.map(order => order.id);
    this.zoneNamesByOrder.set({});
    if (ids.length === 0) {
      return;
    }
    this.lezApi.orderZones(ids)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: rows => {
          const names: Record<number, string[]> = {};
          for (const row of rows ?? []) {
            if (row?.zones?.length) {
              names[row.order_id] = row.zones.map(zone => zone.name);
            }
          }
          this.zoneNamesByOrder.set(names);
        },
        error: () => this.zoneNamesByOrder.set({})
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
    // D4 (LEZ-001): one plan serves one delivery date. Refuse locally, before
    // the dialog, and name the dates so the planner can fix the selection.
    const dates = [...new Set(
      this.selectedOrders
        .map(order => order.delivery_date)
        .filter((date): date is string => !!date)
    )].sort();
    if (dates.length > 1) {
      this.toastService.error(
        `Orders on different delivery dates cannot be planned together: ${dates.join(', ')}.`
      );
      return;
    }

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
            // The server's D4 refusal is more specific than any local text.
            this.toastService.error(
              serverErrorMessage(err, 'Failed to submit optimization job')
            );
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

  onBulkEditStatus(): void {
    if (this.selectedOrders.length === 0) return;
    const dialogRef = this.dialog.open(BulkStatusDialogComponent, {
      width: '440px',
      maxWidth: '90vw',
      data: { selectedCount: this.selectedOrders.length } as BulkStatusDialogData
    });

    dialogRef.afterClosed().subscribe((result: BulkStatusDialogResult | null) => {
      if (!result) return;
      const ids = this.selectedOrders.map(o => o.id);
      this.apiService.bulkUpdateOrderStatus({ ids, status: result.status }).subscribe({
        next: report => {
          this.bulkResult.set(report);
          // Small lists open for immediate reading; large ones stay
          // collapsed behind "View details" so the table keeps its space.
          this.bulkDetailsOpen.set(report.failed.length > 0 && report.failed.length <= 5);
          this.selectedOrders = [];
          this.loadOrders();
          this.toastService.success(
            `${report.updated.length} updated`
            + (report.failed.length > 0 ? `, ${report.failed.length} refused` : ''));
        },
        error: () => {
          this.toastService.error('Bulk update failed; list reloaded.');
          this.loadOrders();
        }
      });
    });
  }

  clearBulkResult(): void {
    this.bulkResult.set(null);
    this.bulkDetailsOpen.set(false);
  }

  toggleBulkDetails(): void {
    this.bulkDetailsOpen.update(open => !open);
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
    // The emitted filter omits cleared fields, so it IS the complete new
    // state: merging would keep stale values (e.g. a cleared date).
    this.currentFilters = { ...newFilters };
    this.currentPage = 0; // Reset to first page
    this.selectedOrders = []; // Single-page selection: never carry across reloads
    this.loadOrders();
  }

  onPageChange(event: PageEvent): void {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.selectedOrders = []; // Single-page selection: never carry across reloads
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
    this.routeCompliance.set(null);
    this.solutionStore.clear();
    this.selectedTabIndex = 0; // Back to Orders tab
  }

  /**
   * Load the Pareto frontier for the active solution. A failed load hides the
   * tab silently (logs once); a null solution clears the state without calling
   * the API. Selecting an id already shown does not refetch.
   */
  private loadParetoFrontier(): void {
    const solution = this.solution();
    if (!solution) {
      this.paretoFrontier.set(null);
      this.paretoLoadedForId = null;
      return;
    }

    const id = solution.id;
    if (this.paretoLoadedForId === id) return;

    this.apiService
      .getPareto(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: frontier => {
          // Ignore a stale response for a solution that is no longer active.
          if (this.solution()?.id !== id) return;
          this.paretoFrontier.set(frontier);
          this.paretoLoadedForId = id;
        },
        error: (err: unknown) => {
          if (this.solution()?.id !== id) return;
          this.paretoFrontier.set(null);
          this.paretoLoadedForId = id;
          console.warn('Failed to load the Pareto frontier:', err);
        }
      });
  }

  /** Emitted by the Pareto view after an apply; refetch for the new revision. */
  onParetoReload(): void {
    this.paretoLoadedForId = null;
    this.loadParetoFrontier();
  }

  /**
   * Set solution from external source (e.g., job monitoring)
   */
  setSolution(solution: SolutionDTO): void {
    this.routeCompliance.set(null);
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
