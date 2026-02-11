import {Component, Input, Output, EventEmitter, HostBinding} from '@angular/core';
import { DepotDTO, VehicleDTO, Stats } from '@core/models';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDividerModule } from '@angular/material/divider';
import { NgFor, NgIf } from '@angular/common';

@Component({
  selector: 'app-sidebar',
  templateUrl: './sidebar.component.html',
  standalone: true,
  imports: [
    MatCheckboxModule,
    MatDividerModule,
    NgIf,
    NgFor
  ],
  styleUrls: ['./sidebar.component.scss']
})
export class SidebarComponent {
  @Input() depots: DepotDTO[] = [];
  @Input() vehicles: VehicleDTO[] = [];
  @Input() stats: Stats = { scheduled: 0, completed: 0, total: 0, unassigned: 0 };
  @Input()
  @HostBinding('class.collapsed')
  collapsed = false;

  @Output() toggleCollapse = new EventEmitter<void>();
  @Output() vehicleSelectionChange = new EventEmitter<number[]>();
  @Output() depotClick = new EventEmitter<DepotDTO>();
  @Output() filterStatusChange = new EventEmitter<string>();

  expandedSections = new Set<string>(['depots', 'vehicles']);
  selectedVehicleIds = new Set<number>();

  onToggleCollapse(): void {
    this.toggleCollapse.emit();
  }

  toggleSection(section: string): void {
    if (this.expandedSections.has(section)) {
      this.expandedSections.delete(section);
    } else {
      this.expandedSections.add(section);
    }
  }

  isSectionExpanded(section: string): boolean {
    return this.expandedSections.has(section);
  }

  onDepotClick(depot: DepotDTO): void {
    this.depotClick.emit(depot);
  }

  onVehicleToggle(vehicleId: number, checked: boolean): void {
    if (checked) {
      this.selectedVehicleIds.add(vehicleId);
    } else {
      this.selectedVehicleIds.delete(vehicleId);
    }
    this.emitSelection();
  }

  selectAllVehicles(): void {
    console.log('1. Button Select All clicked');
    console.log('Current vehicles:', this.vehicles);

    if (!this.vehicles || this.vehicles.length === 0) {
      console.warn('Vehicles list is empty!');
      return;
    }

    this.vehicles.forEach(v => this.selectedVehicleIds.add(v.id));
    console.log('2. IDs selected:', this.selectedVehicleIds);
    this.emitSelection();
  }

  deselectAllVehicles(): void {
    this.selectedVehicleIds.clear();
    // this.emitSelection();
  }

  isVehicleSelected(vehicleId: number): boolean {
    return this.selectedVehicleIds.has(vehicleId);
  }

  onStatClick(type: string): void {
    this.filterStatusChange.emit(type);
  }

  private emitSelection(): void {
    this.vehicleSelectionChange.emit(Array.from(this.selectedVehicleIds));
  }
}
