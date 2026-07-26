import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  OnInit,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs/operators';

import { VehicleDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { ToastService } from '@shared/services/toast.service';

/**
 * Vehicles management (read-only)
 *
 * Migrated from V1 `components/Admin/vehicle-manager.js` + `#vehicles-section`.
 *
 * V1 rendered Edit / Delete icons wired to `VehicleManager.openEdit()` and
 * `VehicleManager.delete()`, neither of which exists in that file - both buttons
 * threw on click - and "+ Add Vehicle" only toasted "Vehicle management coming
 * soon!". Rather than porting dead buttons, this screen is list + search only.
 * Say the word and I'll add real vehicle CRUD once the endpoints are confirmed.
 */
@Component({
  selector: 'app-vehicles-admin',
  standalone: true,
  imports: [],
  templateUrl: './vehicles-admin.component.html',
  styleUrl: './vehicles-admin.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class VehiclesAdminComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly vehicles = signal<readonly VehicleDTO[]>([]);
  readonly loading = signal(false);
  readonly searchTerm = signal('');

  readonly filteredVehicles = computed(() => {
    const query = this.searchTerm().trim().toLowerCase();
    const vehicles = this.vehicles();

    if (!query) return vehicles;
    return vehicles.filter(vehicle =>
      vehicle.vehicle_license_plate.toLowerCase().includes(query)
    );
  });

  ngOnInit(): void {
    this.load();
  }

  onSearch(value: string): void {
    this.searchTerm.set(value);
  }

  /** V1 mapped unknown / busy / failed statuses onto three badge colours. */
  statusBadgeClass(status: string | null | undefined): string {
    const value = status ?? 'AVAILABLE';

    if (value === 'ON_ROUTE' || value === 'BUSY' || value === 'IN_USE') {
      return 'badge badge-warning';
    }
    if (value === 'MAINTENANCE' || value === 'FAILED') {
      return 'badge badge-danger';
    }
    return 'badge badge-success';
  }

  statusLabel(status: string | null | undefined): string {
    return status ?? 'AVAILABLE';
  }

  private load(): void {
    this.loading.set(true);

    // V1 asked for page 0 / size 1000 and rendered everything client-side.
    this.api
      .getVehicles(0, 1000)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: vehicles => this.vehicles.set(vehicles ?? []),
        error: (error: unknown) => {
          console.error('Failed to load vehicles:', error);
          this.toast.error('Failed to load vehicles');
        }
      });
  }
}
