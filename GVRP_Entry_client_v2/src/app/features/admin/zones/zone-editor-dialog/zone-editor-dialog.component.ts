import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

import {
  BranchZoneDTO,
  DayOfWeekCode,
  ENERGY_TYPES,
  EnergyType,
  LEGAL_CLASSES,
  LegalClass,
  ScheduleType,
  WeightMeasure,
  ZoneRuleInput
} from '@core/models';
import { LezApiService } from '@core/services/lez-api.service';
import { ToastService } from '@shared/services/toast.service';
import { serverErrorMessage } from '@shared/utils/api-error.utils';

import { ZoneMapComponent } from '../zone-map/zone-map.component';
import {
  emptyRuleForm,
  fromRuleDTO,
  isSelectorEmpty,
  toRuleInput,
  validateRuleForm,
  ZoneRuleFormModel
} from '../zone-rule-form.utils';

export interface ZoneEditorDialogData {
  /** The COPIED zone being edited; LINKED zones are read-only (D2). */
  zone: BranchZoneDTO;
}

/** Largest accepted GeoJSON file; the server applies its own vertex limit. */
const MAX_FILE_BYTES = 2 * 1024 * 1024;

const DAYS: readonly DayOfWeekCode[] = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];
const SCHEDULE_TYPES: readonly ScheduleType[] = [
  'ALWAYS_FORBIDDEN',
  'FORBIDDEN_DURING',
  'ALLOWED_ONLY'
];
const WEIGHT_MEASURES: readonly WeightMeasure[] = ['GROSS', 'PAYLOAD'];

/**
 * Editor of one COPIED zone (LEZ-001 P1c, C2, D7).
 *
 * Geometry is pasted or uploaded as GeoJSON and previewed on a map; rules are
 * edited through the structured form of `zone-rule-form.utils`. No drawing
 * plugin (design §15). Saving sends the geometry and the whole rule list; a
 * client-side validation error blocks the request, but the server stays the
 * authority.
 */
@Component({
  selector: 'app-zone-editor-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatButtonModule, ZoneMapComponent],
  templateUrl: './zone-editor-dialog.component.html',
  styleUrl: './zone-editor-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ZoneEditorDialogComponent {
  private readonly lez = inject(LezApiService);
  private readonly toast = inject(ToastService);
  private readonly dialogRef = inject<MatDialogRef<ZoneEditorDialogComponent, boolean>>(MatDialogRef);
  readonly data = inject<ZoneEditorDialogData>(MAT_DIALOG_DATA);

  readonly legalClassOptions = LEGAL_CLASSES;
  readonly energyTypeOptions = ENERGY_TYPES;
  readonly dayOptions = DAYS;
  readonly scheduleTypes = SCHEDULE_TYPES;
  readonly weightMeasures = WEIGHT_MEASURES;

  readonly geometryText = signal('');
  readonly geometry = signal<unknown | null>(this.data.zone.geometry ?? null);
  readonly geometryErrors = signal<string[]>([]);
  readonly rules = signal<ZoneRuleFormModel[]>(
    (this.data.zone.rules ?? []).map(rule => fromRuleDTO(rule))
  );
  readonly formErrors = signal<string[]>([]);
  readonly busy = signal(false);

  readonly isCopied = computed(() => this.data.zone.mode === 'COPIED');

  constructor() {
    const existing = this.data.zone.geometry;
    if (existing) {
      this.geometryText.set(JSON.stringify(existing));
    }
  }

  // ---------- geometry ----------

  /** Parses the pasted text; nothing is sent when it is not a polygonal GeoJSON. */
  applyPastedGeometry(): void {
    this.geometryErrors.set([]);
    const text = this.geometryText().trim();
    if (!text) {
      this.geometryErrors.set(['Paste a GeoJSON Polygon or MultiPolygon.']);
      return;
    }
    let parsed: unknown;
    try {
      parsed = JSON.parse(text);
    } catch {
      this.geometryErrors.set(['The text is not valid JSON.']);
      return;
    }
    this.applyGeometry(parsed);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.geometryErrors.set([]);
    if (file.size > MAX_FILE_BYTES) {
      this.geometryErrors.set(['The file is larger than 2 MB.']);
      input.value = '';
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      this.geometryText.set(String(reader.result ?? ''));
      this.applyPastedGeometry();
      input.value = '';
    };
    reader.onerror = () => {
      this.geometryErrors.set(['The file could not be read.']);
      input.value = '';
    };
    reader.readAsText(file);
  }

  private applyGeometry(candidate: unknown): void {
    const polygon = extractPolygon(candidate);
    if (!polygon) {
      this.geometryErrors.set([
        'Only Polygon or MultiPolygon geometry is accepted (a Point is not a zone).'
      ]);
      return;
    }
    this.geometry.set(polygon);
  }

  // ---------- rules ----------

  addRule(): void {
    this.rules.set([...this.rules(), emptyRuleForm()]);
    this.formErrors.set([]);
  }

  removeRule(index: number): void {
    this.rules.set(this.rules().filter((_, i) => i !== index));
  }

  patchRule(index: number, patch: Partial<ZoneRuleFormModel>): void {
    this.rules.set(
      this.rules().map((rule, i) => (i === index ? { ...rule, ...patch } : rule))
    );
  }

  toggleLegalClass(index: number, legalClass: LegalClass, checked: boolean): void {
    const current = this.rules()[index].legalClasses;
    this.patchRule(index, {
      legalClasses: checked
        ? [...current, legalClass]
        : current.filter(value => value !== legalClass)
    });
  }

  toggleEnergyType(index: number, energyType: EnergyType, checked: boolean): void {
    const current = this.rules()[index].energyTypes;
    this.patchRule(index, {
      energyTypes: checked
        ? [...current, energyType]
        : current.filter(value => value !== energyType)
    });
  }

  toggleDay(index: number, day: DayOfWeekCode, checked: boolean): void {
    const current = this.rules()[index].daysOfWeek;
    this.patchRule(index, {
      daysOfWeek: checked ? [...current, day] : current.filter(value => value !== day)
    });
  }

  addInterval(index: number): void {
    const rule = this.rules()[index];
    this.patchRule(index, { intervals: [...rule.intervals, { start: '06:00', end: '09:00' }] });
  }

  removeInterval(ruleIndex: number, intervalIndex: number): void {
    const rule = this.rules()[ruleIndex];
    this.patchRule(ruleIndex, {
      intervals: rule.intervals.filter((_, i) => i !== intervalIndex)
    });
  }

  setInterval(ruleIndex: number, intervalIndex: number, side: 'start' | 'end', value: string): void {
    const rule = this.rules()[ruleIndex];
    this.patchRule(ruleIndex, {
      intervals: rule.intervals.map((interval, i) =>
        i === intervalIndex ? { ...interval, [side]: value } : interval
      )
    });
  }

  setNumber(index: number, key: keyof ZoneRuleFormModel, event: Event): void {
    const raw = (event.target as HTMLInputElement).value;
    const value = raw === '' ? null : Number(raw);
    this.patchRule(index, { [key]: Number.isFinite(value as number) ? value : null } as
      Partial<ZoneRuleFormModel>);
  }

  isEveryVehicle(index: number): boolean {
    return isSelectorEmpty(this.rules()[index]);
  }

  // ---------- save ----------

  save(): void {
    if (!this.isCopied() || this.busy()) {
      return;
    }
    const geometry = this.geometry();
    if (!geometry) {
      this.formErrors.set(['Add the zone geometry first.']);
      return;
    }

    const rules = this.rules();
    const errors: string[] = [];
    rules.forEach((rule, index) => {
      for (const message of validateRuleForm(rule, rules.length)) {
        errors.push(`Rule ${index + 1}: ${message}`);
      }
    });
    if (errors.length > 0) {
      this.formErrors.set(errors);
      return;
    }

    const payload: ZoneRuleInput[] = rules.map(toRuleInput);
    this.formErrors.set([]);
    this.busy.set(true);

    // Geometry first: a refused polygon must not leave new rules behind.
    this.lez.replaceZoneGeometry(this.data.zone.id, geometry).subscribe({
      next: () => {
        this.lez.replaceZoneRules(this.data.zone.id, payload).subscribe({
          next: () => {
            this.busy.set(false);
            this.toast.success('Zone saved');
            this.dialogRef.close(true);
          },
          error: (error: unknown) => {
            this.busy.set(false);
            this.formErrors.set([serverErrorMessage(error, 'Failed to save the rules')]);
          }
        });
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.formErrors.set([serverErrorMessage(error, 'Failed to save the geometry')]);
      }
    });
  }

  cancel(): void {
    this.dialogRef.close(false);
  }
}

/**
 * Returns the GeoJSON `Polygon`/`MultiPolygon` object of a geometry, a Feature
 * or a FeatureCollection, or null when the value is not polygonal.
 */
function extractPolygon(value: unknown): unknown | null {
  if (!value || typeof value !== 'object') {
    return null;
  }
  const node = value as { type?: unknown; coordinates?: unknown; geometry?: unknown; features?: unknown };
  if (node.type === 'Polygon' || node.type === 'MultiPolygon') {
    return Array.isArray(node.coordinates) && node.coordinates.length > 0 ? value : null;
  }
  if (node.type === 'Feature' && node.geometry) {
    return extractPolygon(node.geometry);
  }
  if (node.type === 'FeatureCollection' && Array.isArray(node.features) && node.features.length === 1) {
    return extractPolygon((node.features[0] as { geometry?: unknown })?.geometry);
  }
  return null;
}
