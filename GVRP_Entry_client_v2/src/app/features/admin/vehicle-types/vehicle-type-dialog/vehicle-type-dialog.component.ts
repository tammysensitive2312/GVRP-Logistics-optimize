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

import {
  ENERGY_TYPES,
  EnergyType,
  LEGAL_CLASSES,
  LegalClass,
  VEHICLE_SKILLS,
  VehicleFeatures,
  VehicleSkill,
  VehicleTypeDTO,
  VehicleTypeInputDTO
} from '@core/models';
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
  skills: FormControl<VehicleSkill[]>;
  legalClass: FormControl<LegalClass | null>;
  grossWeightKg: FormControl<number | null>;
  energyType: FormControl<EnergyType | null>;
  emissionStandard: FormControl<number | null>;
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
  readonly skillOptions = VEHICLE_SKILLS;
  readonly legalClassOptions = LEGAL_CLASSES;
  readonly energyTypeOptions = ENERGY_TYPES;
  readonly dialogRef =
    inject<MatDialogRef<VehicleTypeDialogComponent, VehicleTypeInputDTO>>(MatDialogRef);
  private readonly data = inject<VehicleTypeDialogData>(MAT_DIALOG_DATA, { optional: true });

  readonly isEdit = !!this.data?.vehicleType;
  readonly title = this.isEdit ? 'Edit Vehicle Type' : 'Add Vehicle Type';

  /** Read-only server suggestion; applied only when the planner clicks "Use suggestion". */
  readonly suggestedLegalClass: LegalClass | null =
    this.data?.vehicleType?.suggested_legal_class ?? null;

  /** True when an existing type lacks at least one legal attribute (R15 badge hint). */
  readonly missingLegalData = this.isEdit && (
    this.data?.vehicleType?.legal_class == null ||
    this.data?.vehicleType?.gross_weight_kg == null ||
    this.data?.vehicleType?.energy_type == null ||
    this.data?.vehicleType?.emission_standard == null
  );

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
    }),
    skills: new FormControl<VehicleSkill[]>([], { nonNullable: true }),
    legalClass: new FormControl<LegalClass | null>(null),
    grossWeightKg: new FormControl<number | null>(null, {
      validators: [nonNegative()]
    }),
    energyType: new FormControl<EnergyType | null>(null),
    emissionStandard: new FormControl<number | null>(null, {
      validators: [nonNegative()]
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
        maxDuration: vehicleType.max_duration ?? null,
        skills: vehicleType.vehicle_features?.skills ?? [],
        legalClass: vehicleType.legal_class ?? null,
        grossWeightKg: vehicleType.gross_weight_kg ?? null,
        energyType: vehicleType.energy_type ?? null,
        emissionStandard: vehicleType.emission_standard ?? null
      });
    }
  }

  /** Explicit click only: the suggestion never writes itself into the form. */
  useSuggestion(): void {
    if (this.suggestedLegalClass) {
      this.form.controls.legalClass.setValue(this.suggestedLegalClass);
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

    const vehicleFeatures: VehicleFeatures = { skills: raw.skills };
    if (raw.emissionFactor !== null) {
      vehicleFeatures.emission_factor = raw.emissionFactor;
    }

    const payload: VehicleTypeInputDTO = {
      type_name: raw.typeName.trim(),
      capacity: raw.capacity,
      fixed_cost: raw.fixedCost,
      cost_per_km: raw.costPerKm,
      cost_per_hour: raw.costPerHour,
      vehicle_features: vehicleFeatures,
      // Always present: the server PUT replaces the whole row (P1a deviation 9).
      legal_class: raw.legalClass ?? null,
      gross_weight_kg: raw.grossWeightKg ?? null,
      energy_type: raw.energyType ?? null,
      emission_standard: raw.emissionStandard ?? null
    };

    if (raw.maxDistance !== null) payload.max_distance = raw.maxDistance;
    if (raw.maxDuration !== null) payload.max_duration = raw.maxDuration;

    this.dialogRef.close(payload);
  }
}
