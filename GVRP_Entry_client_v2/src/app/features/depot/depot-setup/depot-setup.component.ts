import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { Router } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Subject } from 'rxjs';
import { finalize, switchMap } from 'rxjs/operators';

import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { DepotInputDTO } from '@core/models';
import { DepotStore } from '@core/services/depot.store';
import {
  LocationPickerComponent,
  PickedLocation
} from '@shared/components/location-picker/location-picker.component';
import { GeocodingService } from '@shared/services/geocoding.service';
import { ToastService } from '@shared/services/toast.service';
import {
  latitudeRange,
  longitudeRange,
  notBlank,
  requiredLocation
} from '@shared/utils/geo.validators';

interface DepotFormControls {
  name: FormControl<string>;
  address: FormControl<string>;
  latitude: FormControl<number | null>;
  longitude: FormControl<number | null>;
}

/** Route the user continues to after a depot is saved (V1: FLEET_SETUP chain). */
const NEXT_SETUP_STEP = '/setup/vehicle-types';

const MAX_NAME_LENGTH = 100;

/**
 * Depot Setup screen
 *
 * Migrated from V1 `scripts/components/Form Components/depot-form.js`
 * + `scripts/components/Map Components/depot-map.js` + the
 * `#screen-depot-setup` markup in index.html.
 *
 * Behaviour differences from V1, all deliberate:
 * - Errors from POST /depots are surfaced and the form stays open. V1 wrapped
 *   the call in `handleApiError`, which swallowed failures and still showed
 *   a success toast before navigating away.
 * - The address field is editable. V1 kept it readonly, so a wrong Nominatim
 *   result could not be corrected before saving.
 */
@Component({
  selector: 'app-depot-setup',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatProgressSpinnerModule,
    TranslocoPipe,
    LocationPickerComponent
  ],
  templateUrl: './depot-setup.component.html',
  styleUrl: './depot-setup.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DepotSetupComponent {
  private readonly depotStore = inject(DepotStore);
  private readonly geocoding = inject(GeocodingService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly i18n = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  readonly maxNameLength = MAX_NAME_LENGTH;

  readonly form = new FormGroup<DepotFormControls>(
    {
      name: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, notBlank(), Validators.maxLength(MAX_NAME_LENGTH)]
      }),
      address: new FormControl('', { nonNullable: true }),
      latitude: new FormControl<number | null>(null, { validators: [latitudeRange()] }),
      longitude: new FormControl<number | null>(null, { validators: [longitudeRange()] })
    },
    { validators: [requiredLocation()] }
  );

  readonly saving = signal(false);
  readonly geocodingInProgress = signal(false);
  /** Coordinates handed to the map; also the source for resetting the marker. */
  readonly picked = signal<PickedLocation | null>(null);

  readonly pickedLatitude = computed(() => this.picked()?.latitude ?? null);
  readonly pickedLongitude = computed(() => this.picked()?.longitude ?? null);

  /** Read-only coordinate boxes, same 6-decimal display as V1. */
  readonly latitudeText = computed(() => formatCoordinate(this.pickedLatitude()));
  readonly longitudeText = computed(() => formatCoordinate(this.pickedLongitude()));

  private readonly geocodeRequests = new Subject<PickedLocation>();

  constructor() {
    // switchMap: a newer click cancels the previous Nominatim lookup, which also
    // keeps us within Nominatim's ~1 req/s policy while the user clicks around.
    // The in-progress flag is raised in onLocationPicked and lowered here, so a
    // cancelled lookup can never clear the flag of the request that replaced it.
    this.geocodeRequests
      .pipe(
        switchMap(location =>
          this.geocoding.reverseGeocode(location.latitude, location.longitude)
        ),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(address => {
        this.geocodingInProgress.set(false);
        this.form.controls.address.setValue(address);
      });
  }

  onLocationPicked(location: PickedLocation): void {
    this.picked.set(location);
    this.geocodingInProgress.set(true);

    this.form.patchValue({
      latitude: location.latitude,
      longitude: location.longitude,
      // Immediate feedback; replaced by the resolved address when it arrives.
      address: this.geocoding.formatCoordinates(location.latitude, location.longitude)
    });
    this.form.controls.latitude.markAsDirty();
    this.form.controls.longitude.markAsDirty();

    this.geocodeRequests.next(location);
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

    this.depotStore
      .create(payload)
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => {
          this.toast.success(this.i18n.translate('depotSetup.created'));
          void this.router.navigate([NEXT_SETUP_STEP]);
        },
        error: (error: unknown) => {
          console.error('Failed to create depot:', error);
          this.toast.error(
            extractMessage(error) ?? this.i18n.translate('depotSetup.createFailed')
          );
        }
      });
  }

  /** V1 "Cancel" button: clears the form and removes the map marker. */
  onReset(): void {
    this.form.reset({ name: '', address: '', latitude: null, longitude: null });
    this.picked.set(null);
    this.toast.info(this.i18n.translate('depotSetup.formReset'));
  }

  private buildPayload(): DepotInputDTO | null {
    const { name, address, latitude, longitude } = this.form.getRawValue();

    if (latitude === null || longitude === null) return null;

    return {
      name: name.trim(),
      address: address.trim(),
      latitude,
      longitude
    };
  }

  /** Error order matches V1 `Validator.validateDepot`; wording lives in i18n. */
  private firstErrorMessage(): string {
    const { name, latitude, longitude } = this.form.controls;

    if (name.hasError('required') || name.hasError('notBlank')) {
      return this.i18n.translate('depotSetup.errors.nameRequired');
    }
    if (name.hasError('maxlength')) {
      return this.i18n.translate('depotSetup.errors.nameTooLong', { max: MAX_NAME_LENGTH });
    }
    if (this.form.hasError('requiredLocation')) {
      return this.i18n.translate('depotSetup.errors.locationRequired');
    }
    if (latitude.hasError('latitudeRange')) {
      return this.i18n.translate('depotSetup.errors.latitudeRange');
    }
    if (longitude.hasError('longitudeRange')) {
      return this.i18n.translate('depotSetup.errors.longitudeRange');
    }
    return this.i18n.translate('depotSetup.errors.generic');
  }
}

function formatCoordinate(value: number | null): string {
  return value === null ? '' : value.toFixed(6);
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
