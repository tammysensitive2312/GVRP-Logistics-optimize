import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatDialog } from '@angular/material/dialog';
import { EMPTY, forkJoin } from 'rxjs';
import { finalize, switchMap } from 'rxjs/operators';

import {
  BranchZoneDTO,
  DepotDTO,
  VehicleDTO,
  ZoneTemplateDTO,
  ZoneVerdictDTO,
  ZoneWarningDTO
} from '@core/models';
import { ApiService } from '@core/services/api.service';
import { LezApiService } from '@core/services/lez-api.service';
import { ConfirmService } from '@shared/services/confirm.service';
import { ToastService } from '@shared/services/toast.service';
import { serverErrorMessage } from '@shared/utils/api-error.utils';
import { formatIntervals } from '@shared/utils/lez-format.utils';

import {
  ZoneEditorDialogComponent,
  ZoneEditorDialogData
} from './zone-editor-dialog/zone-editor-dialog.component';
import { ZoneMapComponent } from './zone-map/zone-map.component';

/**
 * Admin page for low-emission zones (LEZ-001 P1c, C1).
 *
 * Templates are read-only (D2): the page can enable (LINKED) or copy (COPIED)
 * one, then manage the branch's own zones. The map preview and the verdict
 * check are read-only too; only the actions named in the design write.
 */
@Component({
  selector: 'app-zones-admin',
  standalone: true,
  imports: [ZoneMapComponent],
  templateUrl: './zones-admin.component.html',
  styleUrl: './zones-admin.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ZonesAdminComponent implements OnInit {
  private readonly lez = inject(LezApiService);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  readonly templates = signal<ZoneTemplateDTO[]>([]);
  readonly zones = signal<BranchZoneDTO[]>([]);
  readonly depots = signal<DepotDTO[]>([]);
  readonly vehicles = signal<VehicleDTO[]>([]);
  readonly loading = signal(true);

  readonly previewGeometry = signal<unknown | null>(null);
  readonly previewTitle = signal('');
  readonly warnings = signal<ZoneWarningDTO[]>([]);

  readonly verdictZoneId = signal<number | null>(null);
  readonly verdictVehicleId = signal<number | null>(null);
  readonly verdictDate = signal(todayIso());
  readonly verdict = signal<ZoneVerdictDTO | null>(null);
  readonly verdictBusy = signal(false);
  readonly verdictError = signal<string | null>(null);

  private readonly linkedTemplateIds = computed(() =>
    new Set(this.zones().filter(zone => zone.mode === 'LINKED').map(zone => zone.template_id))
  );

  readonly warningTexts = computed(() =>
    this.warnings().map(warning => `${this.depotName(warning.depot_id)} is inside this zone.`)
  );

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    forkJoin({
      templates: this.lez.listTemplates(),
      zones: this.lez.listZones(),
      depots: this.api.getDepots(),
      vehicles: this.api.getVehicles()
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: ({ templates, zones, depots, vehicles }) => {
          this.templates.set(templates);
          this.zones.set(zones);
          this.depots.set(depots);
          this.vehicles.set(vehicles);
          if (!this.previewGeometry() && zones.length > 0) {
            this.previewZone(zones[0]);
          }
        },
        error: (error: unknown) => this.toast.error(
          serverErrorMessage(error, 'Failed to load low-emission zones')
        )
      });
  }

  // ---------- preview ----------

  previewTemplate(template: ZoneTemplateDTO): void {
    this.lez.getTemplate(template.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: detail => {
          this.previewGeometry.set(detail.geometry ?? null);
          this.previewTitle.set(`${detail.name} (v${detail.version})`);
          this.warnings.set([]);
        },
        error: (error: unknown) => this.toast.error(
          serverErrorMessage(error, 'Failed to load the template geometry')
        )
      });
  }

  previewZone(zone: BranchZoneDTO): void {
    this.previewGeometry.set(zone.geometry ?? null);
    this.previewTitle.set(zone.name);
    this.warnings.set(zone.warnings ?? []);
  }

  depotName(depotId: number | null | undefined): string {
    if (depotId == null) {
      return 'A depot';
    }
    return this.depots().find(depot => depot.id === depotId)?.name ?? `Depot ${depotId}`;
  }

  // ---------- templates ----------

  linkedExists(template: ZoneTemplateDTO): boolean {
    return this.linkedTemplateIds().has(template.id);
  }

  enable(template: ZoneTemplateDTO): void {
    this.createFromTemplate(template, 'LINKED');
  }

  copy(template: ZoneTemplateDTO): void {
    this.createFromTemplate(template, 'COPIED');
  }

  private createFromTemplate(template: ZoneTemplateDTO, mode: 'LINKED' | 'COPIED'): void {
    this.lez.createZone({ template_id: template.id, mode })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: created => {
          this.toast.success(mode === 'LINKED' ? 'Zone enabled' : 'Zone copied');
          this.warnings.set(created.warnings ?? []);
          this.refreshZones();
          this.previewZone(created);
        },
        error: (error: unknown) => this.toast.error(
          serverErrorMessage(error, 'Failed to create the zone')
        )
      });
  }

  // ---------- the branch's zones ----------

  toggleEnabled(zone: BranchZoneDTO, event: Event): void {
    const input = event.target as HTMLInputElement;
    const enabled = input.checked;
    this.lez.setZoneEnabled(zone.id, enabled)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: updated => this.replaceZone(updated),
        error: (error: unknown) => {
          input.checked = !enabled;
          this.toast.error(serverErrorMessage(error, 'Failed to update the zone'));
        }
      });
  }

  acknowledge(zone: BranchZoneDTO): void {
    const version = zone.template_version ?? zone.source_template_version ?? 0;
    this.confirm.ask({
      title: 'Acknowledge template update',
      message: `The template of "${zone.name}" is now version ${version}. `
        + 'Acknowledge it for this copy?',
      confirmText: 'Acknowledge'
    })
      .pipe(
        switchMap(confirmed => confirmed ? this.lez.acknowledgeZone(zone.id, version) : EMPTY),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: updated => {
          this.replaceZone(updated);
          this.toast.success('Template update acknowledged');
        },
        error: (error: unknown) => this.toast.error(
          serverErrorMessage(error, 'Failed to acknowledge the update')
        )
      });
  }

  remove(zone: BranchZoneDTO): void {
    this.confirm.ask({
      title: 'Delete zone',
      message: `Delete "${zone.name}"? This cannot be undone.`,
      confirmText: 'Delete',
      danger: true
    })
      .pipe(
        switchMap(confirmed => confirmed ? this.lez.deleteZone(zone.id) : EMPTY),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => {
          this.toast.success('Zone deleted');
          this.refreshZones();
        },
        error: (error: unknown) => this.toast.error(
          serverErrorMessage(error, 'Failed to delete the zone')
        )
      });
  }

  openEditor(zone: BranchZoneDTO): void {
    this.dialog
      .open<ZoneEditorDialogComponent, ZoneEditorDialogData, boolean>(
        ZoneEditorDialogComponent,
        { data: { zone }, width: '900px', maxWidth: '96vw', autoFocus: 'first-tabbable' }
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.refreshZones());
  }

  // ---------- verdict ----------

  runVerdict(): void {
    const zoneId = this.verdictZoneId();
    const vehicleId = this.verdictVehicleId();
    if (zoneId == null || vehicleId == null) {
      this.verdictError.set('Choose a zone and a vehicle.');
      return;
    }
    this.verdictError.set(null);
    this.verdictBusy.set(true);
    this.lez.zoneVerdict(zoneId, vehicleId, this.verdictDate())
      .pipe(
        finalize(() => this.verdictBusy.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: verdict => this.verdict.set(verdict),
        error: (error: unknown) => {
          this.verdict.set(null);
          this.verdictError.set(serverErrorMessage(error, 'Failed to check the verdict'));
        }
      });
  }

  verdictKindLabel(kind: ZoneVerdictDTO['kind']): string {
    switch (kind) {
      case 'ALL_DAY':
        return 'Forbidden all day';
      case 'PARTIAL':
        return 'Forbidden during some hours';
      default:
        return 'Allowed';
    }
  }

  forbiddenText(verdict: ZoneVerdictDTO): string {
    return formatIntervals(verdict.forbidden);
  }

  // ---------- events ----------

  onVerdictZone(event: Event): void {
    const value = Number((event.target as HTMLSelectElement).value);
    this.verdictZoneId.set(Number.isFinite(value) && value > 0 ? value : null);
  }

  onVerdictVehicle(event: Event): void {
    const value = Number((event.target as HTMLSelectElement).value);
    this.verdictVehicleId.set(Number.isFinite(value) && value > 0 ? value : null);
  }

  onVerdictDate(event: Event): void {
    this.verdictDate.set((event.target as HTMLInputElement).value);
  }

  // ---------- helpers ----------

  private refreshZones(): void {
    this.lez.listZones()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: zones => this.zones.set(zones),
        error: (error: unknown) => this.toast.error(
          serverErrorMessage(error, 'Failed to reload the zones')
        )
      });
  }

  private replaceZone(updated: BranchZoneDTO): void {
    this.zones.set(this.zones().map(zone => zone.id === updated.id ? updated : zone));
  }
}

function todayIso(): string {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 10);
}
