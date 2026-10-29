import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import {
  RouteComplianceDTO,
  SolutionComplianceDTO,
  SolutionDTO
} from '@core/models';
import { LezApiService } from '@core/services/lez-api.service';
import { MapService } from '@shared/services/map.service';
import {
  complianceStatusLabel,
  violationSummary
} from '@shared/utils/lez-format.utils';
import { orderStops } from '@shared/utils/solution-view.utils';

import { groupGeometryMissing } from './compliance-rows.utils';

export interface ComplianceViolationRow {
  /** One sentence: code, stop name, zone name, server detail. */
  text: string;
  /** Violation code, so the map can tell a leg from a stop. */
  code: string;
  /** Server detail; the leg variant of LEZ_DATA_MISSING is only visible here. */
  detail: string | null;
  /** 0-based plan index of the stop the violation happened at. */
  seq: number;
  /** Summary of several geometry findings; not clickable (UX-LEZ-001, D9). */
  summary?: boolean;
}

export interface ComplianceRouteRow {
  routeId: number | null;
  licensePlate: string;
  status: string;
  statusLabel: string;
  checkedAtText: string | null;
  violations: ComplianceViolationRow[];
}

/**
 * Low-emission compliance of one solution (LEZ-001 P1c, C7, design §3.7).
 *
 * Read-only apart from the explicit "Recheck now" (one POST). A failed request
 * leaves the panel in the unknown state: `complianceChange` emits null and the
 * send rule falls back to today's behaviour (design §3.6). Clicking a violation
 * moves the planner map to the offending leg (C9).
 */
@Component({
  selector: 'app-compliance-panel',
  standalone: true,
  imports: [],
  templateUrl: './compliance-panel.component.html',
  styleUrl: './compliance-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CompliancePanelComponent {
  readonly solution = input.required<SolutionDTO>();
  /** Latest known compliance; null means "unknown", not "compliant". */
  readonly complianceChange = output<SolutionComplianceDTO | null>();
  /** Emitted after a violation moved the planner map (the expanded dialog closes on it). */
  readonly violationFocused = output<void>();

  private readonly lez = inject(LezApiService);
  private readonly mapService = inject(MapService);
  private readonly destroyRef = inject(DestroyRef);

  readonly compliance = signal<SolutionComplianceDTO | null>(null);
  readonly loading = signal(false);
  readonly rechecking = signal(false);
  readonly error = signal<string | null>(null);

  /** Solution id whose compliance is loaded (or was last attempted). */
  private loadedForId: number | null = null;

  readonly rows = computed<ComplianceRouteRow[]>(() => {
    const dto = this.compliance();
    const byRoute = new Map<number, RouteComplianceDTO>(
      (dto?.routes ?? []).map(row => [row.route_id, row])
    );
    const zones = dto?.zones ?? [];

    return this.solution().routes.map(route => {
      const row = route.id != null ? byRoute.get(route.id) ?? null : null;
      const stops = orderStops(route.stops);
      const violations: ComplianceViolationRow[] = (row?.violations ?? []).map(violation => {
        // `seq` is the 0-based plan index of the stop (design §3.8).
        const stopName = stops[violation.seq]?.location_name ?? null;
        const zoneName = zones.find(zone => zone.key === violation.zone_key)?.name
          ?? violation.zone_key
          ?? null;
        return {
          text: violationSummary(violation, stopName, zoneName),
          code: violation.code,
          detail: violation.detail ?? null,
          seq: violation.seq
        };
      });
      return {
        routeId: route.id ?? null,
        licensePlate: route.vehicle_license_plate,
        status: row?.status ?? 'UNKNOWN',
        statusLabel: row ? complianceStatusLabel(row.status) : 'Unknown',
        checkedAtText: row?.checked_at ? row.checked_at.replace('T', ' ').slice(0, 16) : null,
        violations: groupGeometryMissing(violations)
      };
    });
  });

  readonly zoneNames = computed(() =>
    (this.compliance()?.zones ?? []).map(zone => zone.name)
  );

  readonly lezUnassigned = computed(() => this.compliance()?.lez_unassigned ?? []);

  /** One line instead of an empty list when no zone applies and all is well. */
  readonly noZoneApplies = computed(() => {
    const rows = this.rows();
    return !!this.compliance()
      && this.zoneNames().length === 0
      && rows.length > 0
      && rows.every(row => row.status === 'COMPLIANT');
  });

  constructor() {
    effect(() => {
      const id = this.solution().id;
      if (id !== this.loadedForId) {
        // Deferred past the effect context for synchronous mocks (NG0600).
        queueMicrotask(() => this.load());
      }
    });
  }

  load(): void {
    const id = this.solution().id;
    this.loadedForId = id;
    this.loading.set(true);
    this.error.set(null);
    this.lez.solutionCompliance(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: dto => {
          this.loading.set(false);
          this.compliance.set(dto);
          this.complianceChange.emit(dto);
        },
        error: () => {
          this.loading.set(false);
          this.compliance.set(null);
          this.error.set('Compliance is not available right now.');
          this.complianceChange.emit(null);
        }
      });
  }

  /** Explicit recheck: POST, then render the fresh verdict. */
  recheck(): void {
    const id = this.solution().id;
    this.rechecking.set(true);
    this.error.set(null);
    this.lez.recheckCompliance(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: dto => {
          this.rechecking.set(false);
          this.compliance.set(dto);
          this.complianceChange.emit(dto);
        },
        error: () => {
          this.rechecking.set(false);
          this.compliance.set(null);
          this.error.set('Recheck failed; the last known state was cleared.');
          this.complianceChange.emit(null);
        }
      });
  }

  /** Moves the planner map to the offending leg or stop of one violation (C9). */
  focusViolation(row: ComplianceRouteRow, violation: ComplianceViolationRow): void {
    if (row.routeId === null || violation.summary) {
      return;
    }
    this.mapService.focusLeg(row.routeId, violation.seq, violation.code, violation.detail);
    this.violationFocused.emit();
  }
}
