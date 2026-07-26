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
import {
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { Router } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';

import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { DepotDTO, FleetInputDTO, VehicleInputDTO, VehicleTypeDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { DepotStore } from '@core/services/depot.store';
import { VehicleTypeStore } from '@core/services/vehicle-type.store';
import { VndPipe } from '@shared/pipes/vnd.pipe';
import { ConfirmService } from '@shared/services/confirm.service';
import { ToastService } from '@shared/services/toast.service';
import { notBlank } from '@shared/utils/geo.validators';

interface VehicleRowControls {
  licensePlate: FormControl<string>;
  vehicleTypeId: FormControl<number | null>;
  startDepotId: FormControl<number | null>;
  endDepotId: FormControl<number | null>;
}

interface FleetFormControls {
  fleetName: FormControl<string>;
  vehicles: FormArray<FormGroup<VehicleRowControls>>;
}

const PREVIOUS_SETUP_STEP = '/setup/vehicle-types';
const DEPOT_SETUP = '/setup/depot';
const MAIN_SCREEN = '/main';

/**
 * Fleet Setup screen
 *
 * Migrated from V1 `fleet-form.js` + `vehicle-card.js` + the
 * `#screen-fleet-setup` markup. The dynamic vehicle cards become a typed
 * FormArray instead of DOM nodes queried with `document.querySelectorAll`.
 *
 * Deliberate differences from V1:
 * - "Back" goes to the vehicle type step. V1 jumped back to depot setup,
 *   skipping a step of its own wizard - that looks like an oversight.
 * - Removing a vehicle uses the shared confirm dialog instead of `confirm()`.
 * - Create errors are surfaced and the form is preserved.
 */
@Component({
  selector: 'app-fleet-setup',
  standalone: true,
  imports: [ReactiveFormsModule, MatProgressSpinnerModule, TranslocoPipe, VndPipe],
  templateUrl: './fleet-setup.component.html',
  styleUrl: './fleet-setup.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FleetSetupComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly depotStore = inject(DepotStore);
  private readonly vehicleTypeStore = inject(VehicleTypeStore);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly router = inject(Router);
  private readonly i18n = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  readonly depots = this.depotStore.depots;
  readonly vehicleTypes = this.vehicleTypeStore.vehicleTypes;

  readonly loadingData = signal(false);
  readonly saving = signal(false);
  /** Bumped on every add/remove so template computeds re-read the FormArray. */
  readonly vehicleRevision = signal(0);

  readonly form = new FormGroup<FleetFormControls>({
    fleetName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, notBlank()]
    }),
    vehicles: new FormArray<FormGroup<VehicleRowControls>>([])
  });

  readonly vehicleRows = computed(() => {
    this.vehicleRevision();
    return this.form.controls.vehicles.controls;
  });

  readonly totalVehicles = computed(() => this.vehicleRows().length);

  ngOnInit(): void {
    this.loadSetupData();
  }

  addVehicle(): void {
    this.form.controls.vehicles.push(this.createVehicleRow());
    this.vehicleRevision.update(revision => revision + 1);
  }

  removeVehicle(index: number): void {
    if (this.form.controls.vehicles.length <= 1) {
      this.toast.error(this.i18n.translate('fleetSetup.atLeastOneVehicle'));
      return;
    }

    const vehicleNumber = index + 1;

    this.confirm
      .ask({
        title: this.i18n.translate('fleetSetup.removeConfirmTitle', { number: vehicleNumber }),
        confirmText: this.i18n.translate('common.delete'),
        cancelText: this.i18n.translate('common.cancel'),
        danger: true
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(confirmed => {
        if (!confirmed) return;

        this.form.controls.vehicles.removeAt(index);
        this.vehicleRevision.update(revision => revision + 1);
        this.toast.success(this.i18n.translate('fleetSetup.removed', { number: vehicleNumber }));
      });
  }

  /** Summary line V1 rendered under the vehicle type select. */
  selectedTypeInfo(row: FormGroup<VehicleRowControls>): VehicleTypeDTO | null {
    const typeId = row.controls.vehicleTypeId.value;
    if (typeId === null) return null;

    return this.vehicleTypes().find(type => type.id === typeId) ?? null;
  }

  onSubmit(): void {
    if (this.saving()) return;

    const error = this.firstErrorMessage();
    if (error) {
      this.form.markAllAsTouched();
      this.toast.error(error);
      return;
    }

    const payload = this.buildPayload();
    if (!payload) return;

    this.saving.set(true);

    this.api
      .createFleet(payload)
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => {
          this.toast.success(this.i18n.translate('fleetSetup.created'));
          void this.router.navigate([MAIN_SCREEN]);
        },
        error: (error: unknown) => {
          console.error('Failed to create fleet:', error);
          this.toast.error(
            extractMessage(error) ?? this.i18n.translate('fleetSetup.createFailed')
          );
        }
      });
  }

  onBack(): void {
    void this.router.navigate([PREVIOUS_SETUP_STEP]);
  }

  onReset(): void {
    this.form.reset({ fleetName: '' });
    this.form.controls.vehicles.clear();
    this.addVehicle();
    this.toast.info(this.i18n.translate('fleetSetup.formReset'));
  }

  private loadSetupData(): void {
    this.loadingData.set(true);

    forkJoin({
      depots: this.depotStore.load(),
      vehicleTypes: this.vehicleTypeStore.load()
    })
      .pipe(
        finalize(() => this.loadingData.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: ({ depots, vehicleTypes }) => {
          if (!this.guardPrerequisites(depots, vehicleTypes)) return;

          if (this.form.controls.vehicles.length === 0) {
            this.addVehicle();
          }
        },
        error: (error: unknown) => {
          console.error('Failed to load data:', error);
          this.toast.error(extractMessage(error) ?? this.i18n.translate('fleetSetup.loadFailed'));
        }
      });
  }

  /** V1 bounced back to the step that was missing data. */
  private guardPrerequisites(
    depots: readonly DepotDTO[],
    vehicleTypes: readonly VehicleTypeDTO[]
  ): boolean {
    if (depots.length === 0) {
      this.toast.error(this.i18n.translate('fleetSetup.noDepots'));
      void this.router.navigate([DEPOT_SETUP]);
      return false;
    }

    if (vehicleTypes.length === 0) {
      this.toast.error(this.i18n.translate('fleetSetup.noVehicleTypes'));
      void this.router.navigate([PREVIOUS_SETUP_STEP]);
      return false;
    }

    return true;
  }

  private createVehicleRow(): FormGroup<VehicleRowControls> {
    return new FormGroup<VehicleRowControls>({
      licensePlate: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, notBlank()]
      }),
      vehicleTypeId: new FormControl<number | null>(null, {
        validators: [Validators.required]
      }),
      startDepotId: new FormControl<number | null>(null, {
        validators: [Validators.required]
      }),
      endDepotId: new FormControl<number | null>(null, {
        validators: [Validators.required]
      })
    });
  }

  private buildPayload(): FleetInputDTO | null {
    const raw = this.form.getRawValue();

    const vehicles: VehicleInputDTO[] = [];

    for (const row of raw.vehicles) {
      if (
        row.vehicleTypeId === null ||
        row.startDepotId === null ||
        row.endDepotId === null
      ) {
        return null;
      }

      vehicles.push({
        vehicle_license_plate: row.licensePlate.trim(),
        vehicle_type_id: row.vehicleTypeId,
        start_depot_id: row.startDepotId,
        end_depot_id: row.endDepotId
      });
    }

    return {
      fleet_name: raw.fleetName.trim(),
      vehicles
    };
  }

  /**
   * Error order matches V1 `FleetForm.handleSubmit` + `Validator.validateVehicle`
   * (per-vehicle messages carry the vehicle number); wording lives in i18n.
   */
  private firstErrorMessage(): string | null {
    if (this.form.controls.fleetName.invalid) {
      return this.i18n.translate('fleetSetup.errors.nameRequired');
    }

    const rows = this.form.controls.vehicles.controls;

    for (let index = 0; index < rows.length; index++) {
      const number = index + 1;
      const row = rows[index].controls;
      const t = (key: string) =>
        this.i18n.translate(`fleetSetup.errors.${key}`, { number });

      if (row.licensePlate.invalid) return t('plateRequired');
      if (row.vehicleTypeId.invalid) return t('typeRequired');
      if (row.startDepotId.invalid) return t('startDepotRequired');
      if (row.endDepotId.invalid) return t('endDepotRequired');
    }

    if (rows.length === 0) {
      return this.i18n.translate('fleetSetup.errors.noVehicles');
    }

    return null;
  }
}

function extractMessage(error: unknown): string | null {
  if (typeof error === 'object' && error !== null && 'message' in error) {
    const message = (error as { message?: unknown }).message;
    if (typeof message === 'string' && message.trim().length > 0) {
      return message;
    }
  }
  return null;
}
