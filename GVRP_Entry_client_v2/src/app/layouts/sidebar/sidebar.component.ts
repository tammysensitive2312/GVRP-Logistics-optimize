import { Component, Input, Output, EventEmitter } from '@angular/core';
import { DepotDTO, VehicleDTO } from '@core/models';

interface Stats {
  scheduled: number;
  completed: number;
  total: number;
  routes: number;
}

@Component({
  selector: 'app-sidebar',
  templateUrl: './sidebar.component.html',
  standalone: true,
  styleUrls: ['./sidebar.component.scss']
})
export class SidebarComponent {
  @Input() depots: DepotDTO[] = [];
  @Input() vehicles: VehicleDTO[] = [];
  @Input() stats: Stats = { scheduled: 0, completed: 0, total: 0, routes: 0 };
  @Input() collapsed = false;

  @Output() toggleCollapse = new EventEmitter<void>();
  @Output() vehicleSelectionChange = new EventEmitter<number[]>();
  @Output() depotClick = new EventEmitter<DepotDTO>();

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
    this.vehicles.forEach(v => this.selectedVehicleIds.add(v.id));
    this.emitSelection();
  }

  deselectAllVehicles(): void {
    this.selectedVehicleIds.clear();
    this.emitSelection();
  }

  isVehicleSelected(vehicleId: number): boolean {
    return this.selectedVehicleIds.has(vehicleId);
  }

  onStatClick(type: string): void {
    // TODO: Filter orders by status
    console.log('Stat clicked:', type);
  }

  private emitSelection(): void {
    this.vehicleSelectionChange.emit(Array.from(this.selectedVehicleIds));
  }
}
