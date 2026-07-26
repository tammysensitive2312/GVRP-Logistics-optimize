import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  OnInit,
  signal
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatDialog} from '@angular/material/dialog';
import {switchMap} from 'rxjs/operators';
import {EMPTY, Observable} from 'rxjs';

import {VehicleTypeDTO, VehicleTypeInputDTO} from '@core/models';
import {VehicleTypeStore} from '@core/services/vehicle-type.store';
import {TranslocoPipe} from '@jsverse/transloco';

import {VndPipe} from '@shared/pipes/vnd.pipe';
import {ConfirmService} from '@shared/services/confirm.service';
import {ToastService} from '@shared/services/toast.service';

import {
  VehicleTypeDialogComponent,
  VehicleTypeDialogData
} from './vehicle-type-dialog/vehicle-type-dialog.component';

/**
 * Vehicle Types management
 */
@Component({
  selector: 'app-vehicle-types-admin',
  standalone: true,
  imports: [VndPipe, TranslocoPipe],
  templateUrl: './vehicle-types-admin.component.html',
  styleUrl: './vehicle-types-admin.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class VehicleTypesAdminComponent implements OnInit {
  private readonly store = inject(VehicleTypeStore);
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = this.store.loading;
  readonly searchTerm = signal('');

  readonly filteredTypes = computed(() => {
    const query = this.searchTerm().trim().toLowerCase();
    const types = this.store.vehicleTypes();

    if (!query) return types;
    return types.filter(type => type.name.toLowerCase().includes(query));
  });

  ngOnInit(): void {
    this.reload();
  }

  onSearch(value: string): void {
    this.searchTerm.set(value);
  }

  openAdd(): void {
    this.openDialog({})
      .pipe(
        switchMap(payload => this.store.create(payload)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(this.saveObserver('Vehicle type created!'));
  }

  openEdit(vehicleType: VehicleTypeDTO): void {
    this.openDialog({vehicleType})
      .pipe(
        switchMap(payload => this.store.update(vehicleType.id, payload)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(this.saveObserver('Vehicle type updated!'));
  }

  remove(vehicleType: VehicleTypeDTO): void {
    this.confirm
      .ask({
        title: 'Are you sure you want to delete this vehicle type?',
        message: vehicleType.name,
        confirmText: 'Delete',
        cancelText: 'Cancel',
        danger: true
      })
      .pipe(
        switchMap(confirmed =>
          confirmed ? this.store.remove(vehicleType.id) : EMPTY
        ),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => this.toast.success('Vehicle type deleted successfully!'),
        error: (error: unknown) => {
          console.error('Failed to delete:', error);
          this.toast.error('Failed to delete vehicle type');
        }
      });
  }

  private openDialog(data: VehicleTypeDialogData): Observable<VehicleTypeInputDTO> {
    return this.dialog
      .open<VehicleTypeDialogComponent, VehicleTypeDialogData, VehicleTypeInputDTO>(
        VehicleTypeDialogComponent,
        {data, width: '640px', maxWidth: '95vw', autoFocus: 'first-tabbable'}
      )
      .afterClosed()
      .pipe(
        // Dismissed dialogs emit undefined; only continue with a real payload.
        switchMap(payload => (payload ? [payload] : EMPTY))
      );
  }

  private saveObserver(successMessage: string) {
    return {
      next: () => this.toast.success(successMessage),
      error: (error: unknown) => {
        console.error('Failed to save:', error);
        this.toast.error('Failed to save vehicle type');
      }
    };
  }

  private reload(): void {
    this.store
      .load()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        error: (error: unknown) => {
          console.error('Failed to load vehicle types:', error);
          this.toast.error('Failed to load vehicle types');
        }
      });
  }
}
