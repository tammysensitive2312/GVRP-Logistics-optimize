import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { DepotDTO, OrderDTO, VehicleDTO, SolutionDTO } from '@core/models';
import { AuthService } from '@core/services/auth.service';
import { StateService } from '@core/services/state.service';

interface Stats {
  scheduled: number;
  completed: number;
  total: number;
  routes: number;
}

@Component({
  selector: 'app-main',
  templateUrl: './main.component.html',
  standalone: true,
  styleUrls: ['./main.component.scss']
})
export class MainComponent implements OnInit, OnDestroy {
  // Data
  depots: DepotDTO[] = [];
  vehicles: VehicleDTO[] = [];
  orders: OrderDTO[] = [];
  solution: SolutionDTO | null = null;

  // Stats
  stats: Stats = {
    scheduled: 0,
    completed: 0,
    total: 0,
    routes: 0
  };

  // UI State
  highlightedOrderId: number | null = null;

  // Resize
  private isResizing = false;
  private startY = 0;
  private startHeight = 0;

  private destroy$ = new Subject<void>();

  constructor(
    private authService: AuthService,
    private stateService: StateService
  ) {}

  ngOnInit(): void {
    this.loadInitialData();
    this.subscribeToState();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadInitialData(): void {
    // TODO: Load from API
    console.log('Loading initial data...');
  }

  private subscribeToState(): void {
    // Subscribe to highlighted order
    this.stateService.highlightedOrder$
      .pipe(takeUntil(this.destroy$))
      .subscribe(orderId => {
        this.highlightedOrderId = orderId;
      });
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
    // Handle sidebar collapse
  }

  onVehicleSelectionChange(selectedIds: number[]): void {
    this.stateService.setSelectedVehicles(selectedIds);
  }

  onOrderSelectionChange(selectedIds: number[]): void {
    this.stateService.setSelectedOrders(selectedIds);
  }

  onOrderClicked(orderId: number): void {
    this.stateService.highlightOrder(orderId);
  }
}
