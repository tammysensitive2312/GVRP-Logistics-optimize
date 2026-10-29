import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { forkJoin } from 'rxjs';
import { finalize, switchMap } from 'rxjs/operators';
import { EMPTY } from 'rxjs';

import {
  BranchZoneDTO,
  DayOfWeekCode,
  VehicleDTO,
  VehiclePermitDTO,
  VehiclePermitInput
} from '@core/models';
import { LezApiService } from '@core/services/lez-api.service';
import { ConfirmService } from '@shared/services/confirm.service';
import { ToastService } from '@shared/services/toast.service';
import { serverErrorMessage } from '@shared/utils/api-error.utils';
import { formatIntervals, hhMmToMinutes } from '@shared/utils/lez-format.utils';

export interface VehiclePermitsDialogData {
  vehicle: VehicleDTO;
}

interface IntervalForm {
  start: string;
  end: string;
}

const DAYS: readonly DayOfWeekCode[] = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];

/** Local ISO date (yyyy-mm-dd); `toISOString()` would shift the day in +07:00. */
function isoDate(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/**
 * Default permit window: today until the same day next year. The API requires
 * both bounds (the columns are NOT NULL), so a new permit cannot start blank.
 */
function defaultValidity(): { from: string; to: string } {
  const from = new Date();
  const to = new Date(from.getFullYear() + 1, from.getMonth(), from.getDate());
  return { from: isoDate(from), to: isoDate(to) };
}

/**
 * Permits of one vehicle for the branch's zones (LEZ-001 P1c, C4).
 *
 * "Whole day" sends `schedule: null`; "Only during" sends `ALLOWED_ONLY` with
 * the intervals (P1a deviation 2: a permit covers the minutes its schedule does
 * not forbid). `ALWAYS_FORBIDDEN` is never offered for a permit.
 */
@Component({
  selector: 'app-vehicle-permits-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatButtonModule],
  templateUrl: './vehicle-permits-dialog.component.html',
  styleUrl: './vehicle-permits-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class VehiclePermitsDialogComponent implements OnInit {
  private readonly lez = inject(LezApiService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  readonly dialogRef = inject<MatDialogRef<VehiclePermitsDialogComponent, boolean>>(MatDialogRef);
  readonly data = inject<VehiclePermitsDialogData>(MAT_DIALOG_DATA);

  readonly dayOptions = DAYS;

  readonly permits = signal<VehiclePermitDTO[]>([]);
  readonly zones = signal<BranchZoneDTO[]>([]);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  /** Null when no form is open; a permit id when editing an existing one. */
  readonly editingId = signal<number | null>(null);
  readonly formOpen = signal(false);
  readonly zoneId = signal<number | null>(null);
  readonly permitNo = signal('');
  readonly wholeDay = signal(true);
  readonly days = signal<DayOfWeekCode[]>([]);
  readonly intervals = signal<IntervalForm[]>([{ start: '06:00', end: '09:00' }]);
  readonly validFrom = signal('');
  readonly validTo = signal('');
  readonly note = signal('');
  readonly formErrors = signal<string[]>([]);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    forkJoin({
      permits: this.lez.listPermits(this.data.vehicle.id),
      zones: this.lez.listZones()
    })
      .pipe(
        finalize(() => this.loading.set(false))
      )
      .subscribe({
        next: ({ permits, zones }) => {
          this.permits.set(permits ?? []);
          this.zones.set(zones ?? []);
        },
        error: (error: unknown) => this.toast.error(
          serverErrorMessage(error, 'Failed to load permits')
        )
      });
  }

  zoneName(branchZoneId: number): string {
    return this.zones().find(zone => zone.id === branchZoneId)?.name
      ?? `Zone ${branchZoneId}`;
  }

  scheduleText(permit: VehiclePermitDTO): string {
    if (!permit.schedule) {
      return 'Whole day';
    }
    const intervals = (permit.schedule.intervals ?? [])
      .map(([start, end]) => [hhMmToMinutes(start) ?? 0, hhMmToMinutes(end) ?? 0] as const);
    return `Only during ${formatIntervals(intervals)}`;
  }

  // ---------- form ----------

  startCreate(): void {
    const validity = defaultValidity();
    this.editingId.set(null);
    this.formOpen.set(true);
    this.zoneId.set(this.zones().find(zone => zone.enabled)?.id ?? null);
    this.permitNo.set('');
    this.wholeDay.set(true);
    this.days.set([]);
    this.intervals.set([{ start: '06:00', end: '09:00' }]);
    this.validFrom.set(validity.from);
    this.validTo.set(validity.to);
    this.note.set('');
    this.formErrors.set([]);
  }

  startEdit(permit: VehiclePermitDTO): void {
    this.editingId.set(permit.id);
    this.formOpen.set(true);
    this.zoneId.set(permit.branch_zone_id);
    this.permitNo.set(permit.permit_no);
    this.wholeDay.set(permit.schedule == null);
    this.days.set([...(permit.schedule?.daysOfWeek ?? [])]);
    this.intervals.set(
      (permit.schedule?.intervals ?? []).map(([start, end]) => ({ start, end }))
    );
    this.validFrom.set(permit.valid_from ?? '');
    this.validTo.set(permit.valid_to ?? '');
    this.note.set(permit.note ?? '');
    this.formErrors.set([]);
  }

  cancelForm(): void {
    this.formOpen.set(false);
    this.formErrors.set([]);
  }

  addInterval(): void {
    this.intervals.set([...this.intervals(), { start: '16:00', end: '19:30' }]);
  }

  removeInterval(index: number): void {
    this.intervals.set(this.intervals().filter((_, i) => i !== index));
  }

  setInterval(index: number, side: 'start' | 'end', value: string): void {
    this.intervals.set(
      this.intervals().map((interval, i) =>
        i === index ? { ...interval, [side]: value } : interval
      )
    );
  }

  toggleDay(day: DayOfWeekCode, checked: boolean): void {
    const current = this.days();
    this.days.set(checked ? [...current, day] : current.filter(value => value !== day));
  }

  save(): void {
    if (this.busy()) {
      return;
    }
    const errors = this.validate();
    if (errors.length > 0) {
      this.formErrors.set(errors);
      return;
    }

    const payload = this.buildPayload();
    const vehicleId = this.data.vehicle.id;
    const editingId = this.editingId();
    const request = editingId == null
      ? this.lez.createPermit(vehicleId, payload)
      : this.lez.updatePermit(vehicleId, editingId, payload);

    this.busy.set(true);
    request
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => {
          this.toast.success(editingId == null ? 'Permit added' : 'Permit updated');
          this.formOpen.set(false);
          this.load();
        },
        error: (error: unknown) => this.formErrors.set([
          serverErrorMessage(error, 'Failed to save the permit')
        ])
      });
  }

  remove(permit: VehiclePermitDTO): void {
    this.confirm.ask({
      title: 'Delete permit',
      message: `Delete permit ${permit.permit_no} for ${this.zoneName(permit.branch_zone_id)}?`,
      confirmText: 'Delete',
      danger: true
    })
      .pipe(
        switchMap(confirmed => confirmed
          ? this.lez.deletePermit(this.data.vehicle.id, permit.id)
          : EMPTY)
      )
      .subscribe({
        next: () => {
          this.toast.success('Permit deleted');
          this.load();
        },
        error: (error: unknown) => this.toast.error(
          serverErrorMessage(error, 'Failed to delete the permit')
        )
      });
  }

  close(): void {
    this.dialogRef.close(true);
  }

  // ---------- helpers ----------

  /** `schedule: null` is the whole day; otherwise `ALLOWED_ONLY` with intervals. */
  buildPayload(): VehiclePermitInput {
    const wholeDay = this.wholeDay();
    return {
      branch_zone_id: this.zoneId() as number,
      permit_no: this.permitNo().trim(),
      schedule: wholeDay
        ? null
        : {
            type: 'ALLOWED_ONLY',
            ...(this.days().length > 0 ? { daysOfWeek: [...this.days()] } : {}),
            intervals: this.intervals().map((interval): [string, string] =>
              [interval.start, interval.end])
          },
      valid_from: this.validFrom() || null,
      valid_to: this.validTo() || null,
      note: this.note().trim() || null
    };
  }

  private validate(): string[] {
    const errors: string[] = [];
    if (this.zoneId() == null) {
      errors.push('Choose a zone.');
    }
    if (!this.permitNo().trim()) {
      errors.push('Permit number is required.');
    }
    if (!this.wholeDay()) {
      if (this.intervals().length === 0) {
        errors.push('Add at least one interval, or use the whole day.');
      }
      for (const interval of this.intervals()) {
        if (hhMmToMinutes(interval.start) == null || hhMmToMinutes(interval.end) == null) {
          errors.push('Times must be HH:mm.');
          break;
        }
        if (interval.start === interval.end) {
          errors.push('An interval may not start and end at the same time.');
          break;
        }
      }
    }
    if (this.validFrom() && this.validTo() && this.validTo() < this.validFrom()) {
      errors.push('"Valid to" must be on or after "Valid from".');
    }
    // Both bounds are required on create and on edit: the columns are NOT NULL
    // and the server rejects a null bound, so a cleared bound is an error
    // (review-p1c R5). A new permit still opens with a one-year window.
    if (!this.validFrom()) {
      errors.push('"Valid from" is required.');
    }
    if (!this.validTo()) {
      errors.push('"Valid to" is required.');
    }
    return [...new Set(errors)];
  }
}
