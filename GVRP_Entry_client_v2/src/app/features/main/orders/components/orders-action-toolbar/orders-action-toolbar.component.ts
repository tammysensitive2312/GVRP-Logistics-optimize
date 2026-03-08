import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatMenuModule } from '@angular/material/menu';

@Component({
  selector: 'app-orders-action-toolbar',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatMenuModule
  ],
  templateUrl: './orders-action-toolbar.component.html',
  styleUrl: './orders-action-toolbar.component.scss'
})
export class OrdersActionToolbarComponent {

  @Input() selectedCount: number = 0;
  @Input() selectedVehiclesCount: number = 0;
  @Input() hasSelection: boolean = false;
  @Input() loading: boolean = false;

  // Main actions
  @Output() addClick = new EventEmitter<void>();
  @Output() importClick = new EventEmitter<void>();
  @Output() selectAllClick = new EventEmitter<void>();
  @Output() planRoutesClick = new EventEmitter<void>();

  // Dropdown actions (bulk actions)
  @Output() exportClick = new EventEmitter<void>();
  @Output() deleteSelectedClick = new EventEmitter<void>();
  @Output() bulkEditClick = new EventEmitter<void>();

  onAddClick(): void {
    this.addClick.emit();
  }

  onImportClick(): void {
    this.importClick.emit();
  }

  onSelectAllClick(): void {
    this.selectAllClick.emit();
  }

  onPlanRoutesClick(): void {
    if (this.canPlanRoutes()) {
      this.planRoutesClick.emit();
    }
  }

  onExportClick(): void {
    this.exportClick.emit();
  }

  onDeleteSelectedClick(): void {
    if (this.hasSelection) {
      this.deleteSelectedClick.emit();
    }
  }

  onBulkEditClick(): void {
    if (this.hasSelection) {
      this.bulkEditClick.emit();
    }
  }

  /**
   * Check if Plan Routes button should be enabled
   */
  canPlanRoutes(): boolean {
    return this.selectedCount > 0 && this.selectedVehiclesCount > 0;
  }

  /**
   * Get tooltip for Plan Routes button
   */
  getPlanRoutesTooltip(): string {
    if (this.selectedCount === 0) {
      return 'Please select orders first';
    }
    if (this.selectedVehiclesCount === 0) {
      return 'Please select vehicles from sidebar';
    }
    return `Plan routes for ${this.selectedCount} orders using ${this.selectedVehiclesCount} vehicles`;
  }
}
