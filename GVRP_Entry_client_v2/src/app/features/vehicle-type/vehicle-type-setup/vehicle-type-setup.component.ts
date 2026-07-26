import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { finalize } from 'rxjs/operators';

import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { VehicleFeatures, VehicleTypeInputDTO } from '@core/models';
import { VehicleTypeStore } from '@core/services/vehicle-type.store';
import { VndPipe } from '@shared/pipes/vnd.pipe';
import { ToastService } from '@shared/services/toast.service';
import { notBlank } from '@shared/utils/geo.validators';
import {
  greaterThanZeroWhenPresent,
  nonNegative
} from '@shared/utils/number.validators';

interface VehicleTypeFormControls {
  typeName: FormControl<string>;
  capacity: FormControl<number | null>;
  emissionFactor: FormControl<number | null>;
  fixedCost: FormControl<number | null>;
  costPerKm: FormControl<number | null>;
  costPerHour: FormControl<number | null>;
  maxDistance: FormControl<number | null>;
  maxDuration: FormControl<number | null>;
}

const PREVIOUS_SETUP_STEP = '/setup/depot';
const NEXT_SETUP_STEP = '/setup/fleet';

/**
 * Vehicle Type Setup screen
 *
 * Migrated from V1 `scripts/components/Form Components/vehicle-type-form.js`
 * and the `#screen-vehicle-type-setup` markup in index.html.
 *
 * Deliberate differences from V1:
 * - Create errors are surfaced and the form keeps its values (V1's
 *   `handleApiError` swallowed them and only logged).
 * - The new type is merged into the cached list instead of refetching the whole
 *   list after every create.
 * - The "Continue" button is disabled until at least one vehicle type exists. V1 let you
 *   skip ahead, and `app.js checkSetupStatus` then bounced you straight back.
 */
@Component({
  selector: 'app-vehicle-type-setup',
  standalone: true,
  imports: [ReactiveFormsModule, MatProgressSpinnerModule, TranslocoPipe, VndPipe],
  templateUrl: './vehicle-type-setup.component.html',
  styleUrl: './vehicle-type-setup.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class VehicleTypeSetupComponent implements OnInit {
  private readonly store = inject(VehicleTypeStore);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly i18n = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  readonly vehicleTypes = this.store.vehicleTypes;
  readonly listLoading = this.store.loading;
  readonly hasVehicleTypes = this.store.hasVehicleTypes;

  readonly saving = signal(false);

  readonly form = new FormGroup<VehicleTypeFormControls>({
    typeName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, notBlank()]
    }),
    capacity: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(1)]
    }),
    emissionFactor: new FormControl<number | null>(null),
    fixedCost: new FormControl<number | null>(null, {
      validators: [Validators.required, nonNegative()]
    }),
    costPerKm: new FormControl<number | null>(null, {
      validators: [Validators.required, nonNegative()]
    }),
    costPerHour: new FormControl<number | null>(null, {
      validators: [Validators.required, nonNegative()]
    }),
    maxDistance: new FormControl<number | null>(null, {
      validators: [greaterThanZeroWhenPresent()]
    }),
    maxDuration: new FormControl<number | null>(null, {
      validators: [greaterThanZeroWhenPresent()]
    })
  });

  ngOnInit(): void {
    this.loadVehicleTypes();
  }

  onSubmit(): void {
    if (this.saving()) return;

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.toast.error(this.firstErrorMessage());
      return;
    }

    const payload = this.buildPayload();
    if (!payload) return;

    this.saving.set(true);

    this.store
      .create(payload)
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => {
          this.toast.success(this.i18n.translate('vehicleTypeSetup.created'));
          this.form.reset();
        },
        error: (error: unknown) => {
          console.error('Failed to create vehicle type:', error);
          this.toast.error(
            extractMessage(error) ?? this.i18n.translate('vehicleTypeSetup.createFailed')
          );
        }
      });
  }

  onBack(): void {
    void this.router.navigate([PREVIOUS_SETUP_STEP]);
  }

  onContinue(): void {
    if (!this.hasVehicleTypes()) return;
    void this.router.navigate([NEXT_SETUP_STEP]);
  }

  private loadVehicleTypes(): void {
    this.store
      .load()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        // The list itself renders from the store's signals; the empty state
        // doubles as V1's error state, which also showed an empty list.
        error: (error: unknown) => {
          console.error('Failed to load vehicle types:', error);
          this.toast.error(
            extractMessage(error) ?? this.i18n.translate('vehicleTypeSetup.loadFailed')
          );
        }
      });
  }

  private buildPayload(): VehicleTypeInputDTO | null {
    const raw = this.form.getRawValue();

    if (raw.capacity === null || raw.fixedCost === null) return null;
    if (raw.costPerKm === null || raw.costPerHour === null) return null;

    // V1 always sent a vehicle_features object, empty when no emission factor.
    const vehicleFeatures: VehicleFeatures = {};
    if (raw.emissionFactor !== null) {
      vehicleFeatures.emission_factor = raw.emissionFactor;
    }

    const payload: VehicleTypeInputDTO = {
      type_name: raw.typeName.trim(),
      vehicle_features: vehicleFeatures,
      capacity: raw.capacity,
      fixed_cost: raw.fixedCost,
      cost_per_km: raw.costPerKm,
      cost_per_hour: raw.costPerHour
    };

    if (raw.maxDistance !== null) payload.max_distance = raw.maxDistance;
    if (raw.maxDuration !== null) payload.max_duration = raw.maxDuration;

    return payload;
  }

  /** Error order matches V1 `Validator.validateVehicleType`; wording lives in i18n. */
  private firstErrorMessage(): string {
    const c = this.form.controls;

    const t = (key: string) => this.i18n.translate(`vehicleTypeSetup.errors.${key}`);

    if (c.typeName.invalid) return t('nameRequired');
    if (c.capacity.invalid) return t('capacityPositive');
    if (c.fixedCost.invalid) return t('fixedCostNonNegative');
    if (c.costPerKm.invalid) return t('costPerKmNonNegative');
    if (c.costPerHour.invalid) return t('costPerHourNonNegative');
    if (c.maxDistance.invalid) return t('maxDistancePositive');
    if (c.maxDuration.invalid) return t('maxDurationPositive');

    return t('generic');
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
