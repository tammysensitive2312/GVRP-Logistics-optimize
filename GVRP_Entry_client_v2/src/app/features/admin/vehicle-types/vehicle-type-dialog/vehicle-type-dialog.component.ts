import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';

import { VehicleFeatures, VehicleTypeDTO, VehicleTypeInputDTO } from '@core/models';
import { notBlank } from '@shared/utils/geo.validators';
import {
  greaterThanZeroWhenPresent,
  nonNegative
} from '@shared/utils/number.validators';

export interface VehicleTypeDialogData {
  /** Present when editing; omitted when adding. */
  vehicleType?: VehicleTypeDTO;
}

interface VehicleTypeDialogControls {
  typeName: FormControl<string>;
  capacity: FormControl<number | null>;
  emissionFactor: FormControl<number | null>;
  fixedCost: FormControl<number | null>;
  costPerKm: FormControl<number | null>;
  costPerHour: FormControl<number | null>;
  maxDistance: FormControl<number | null>;
  maxDuration: FormControl<number | null>;
}

/**
 * Add / Edit Vehicle Type dialog
 *
 * Replaces V1's `#vehicle-type-modal` (hand-rolled modal + `.active` class)
 * from vehicle-type-manager.js. Closes with the request payload, so the caller
 * decides between create and update.
 *
 * V1 parsed `vehicle_features` with `JSON.parse(type.vehicle_features)` inside a
 * try/catch - the API returns an object, so that parse always failed and the
 * emission factor never pre-filled when editing. Here the object is read directly.
 */
@Component({
  selector: 'app-vehicle-type-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, TranslocoPipe],
  templateUrl: './vehicle-type-dialog.component.html',
  styleUrl: './vehicle-type-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class VehicleTypeDialogComponent {
  readonly dialogRef =
    inject<MatDialogRef<VehicleTypeDialogComponent, VehicleTypeInputDTO>>(MatDialogRef);
  private readonly data = inject<VehicleTypeDialogData>(MAT_DIALOG_DATA, { optional: true });

  readonly isEdit = !!this.data?.vehicleType;
  readonly title = this.isEdit ? 'Edit Vehicle Type' : 'Add Vehicle Type';

  readonly form = new FormGroup<VehicleTypeDialogControls>({
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

  constructor() {
    const vehicleType = this.data?.vehicleType;
    if (vehicleType) {
      this.form.patchValue({
        typeName: vehicleType.name,
        capacity: vehicleType.capacity,
        emissionFactor: vehicleType.vehicle_features?.emission_factor ?? null,
        fixedCost: vehicleType.fixed_cost,
        costPerKm: vehicleType.cost_per_km,
        costPerHour: vehicleType.cost_per_hour,
        maxDistance: vehicleType.max_distance ?? null,
        maxDuration: vehicleType.max_duration ?? null
      });
    }
  }

  onSave(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();

    if (
      raw.capacity === null ||
      raw.fixedCost === null ||
      raw.costPerKm === null ||
      raw.costPerHour === null
    ) {
      return;
    }

    const vehicleFeatures: VehicleFeatures = {};
    if (raw.emissionFactor !== null) {
      vehicleFeatures.emission_factor = raw.emissionFactor;
    }

    const payload: VehicleTypeInputDTO = {
      type_name: raw.typeName.trim(),
      capacity: raw.capacity,
      fixed_cost: raw.fixedCost,
      cost_per_km: raw.costPerKm,
      cost_per_hour: raw.costPerHour,
      vehicle_features: vehicleFeatures
    };

    if (raw.maxDistance !== null) payload.max_distance = raw.maxDistance;
    if (raw.maxDuration !== null) payload.max_duration = raw.maxDuration;

    this.dialogRef.close(payload);
  }
}
