import { Component, Input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';

import { BranchZoneDTO, DepotDTO, VehicleDTO, ZoneTemplateDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { LezApiService } from '@core/services/lez-api.service';
import { ConfirmService } from '@shared/services/confirm.service';
import { ToastService } from '@shared/services/toast.service';

import { ZonesAdminComponent } from './zones-admin.component';

/** Leaflet needs a laid-out container; the page only passes data to the preview. */
@Component({
  selector: 'app-zone-map',
  standalone: true,
  template: '<div class="zone-map-stub"></div>'
})
class ZoneMapStubComponent {
  @Input() geometry: unknown = null;
  @Input() depots: unknown = [];
  @Input() height = '320px';
}

/** ZP-T1: the admin zones page with a mocked Entry API (LEZ-001 P1c). */
describe('ZonesAdminComponent', () => {
  let fixture: ComponentFixture<ZonesAdminComponent>;
  let component: ZonesAdminComponent;
  let lez: jasmine.SpyObj<LezApiService>;
  let toast: jasmine.SpyObj<ToastService>;
  let confirm: jasmine.SpyObj<ConfirmService>;
  let dialogOpen: jasmine.Spy;

  const template: ZoneTemplateDTO = {
    id: 5,
    code: 'HN-LEZ',
    name: 'Hanoi LEZ',
    jurisdiction: 'Hanoi',
    version: 2
  };

  const depot: DepotDTO = {
    id: 9,
    name: 'Central depot',
    address: 'Hanoi',
    latitude: 21.01,
    longitude: 105.8,
    branch_id: 1,
    created_at: '2027-01-01T00:00:00',
    updated_at: '2027-01-01T00:00:00'
  };

  const vehicle: VehicleDTO = {
    id: 4,
    branch_id: 1,
    vehicle_license_plate: '29A-12345',
    vehicle_type_id: 2,
    vehicle_type_name: 'Truck 3.5t',
    start_depot_id: 9,
    end_depot_id: 9,
    capacity: 3500,
    status: 'AVAILABLE'
  };

  const linkedZone: BranchZoneDTO = {
    id: 17,
    mode: 'LINKED',
    template_id: 5,
    name: 'Hanoi LEZ',
    enabled: true,
    rules: [],
    update_available: false
  };

  const copiedZone: BranchZoneDTO = {
    id: 18,
    mode: 'COPIED',
    template_id: 5,
    name: 'Copy of Hanoi LEZ',
    enabled: true,
    rules: [{ selector: {} }],
    update_available: true
  };

  const setup = async (
    zones: BranchZoneDTO[] = [linkedZone, copiedZone]
  ): Promise<void> => {
    lez = jasmine.createSpyObj<LezApiService>('LezApiService', [
      'listTemplates', 'listZones', 'getTemplate', 'createZone', 'setZoneEnabled',
      'acknowledgeZone', 'deleteZone', 'zoneVerdict'
    ]);
    const api = jasmine.createSpyObj<Pick<ApiService, 'getDepots' | 'getVehicles'>>(
      'ApiService', ['getDepots', 'getVehicles']
    );
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error', 'warning', 'info']);
    confirm = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['ask']);
    dialogOpen = jasmine.createSpy('open').and.returnValue({afterClosed: () => of(false)});

    lez.listTemplates.and.returnValue(of([template]));
    lez.listZones.and.returnValue(of(zones));
    api.getDepots.and.returnValue(of([depot]));
    api.getVehicles.and.returnValue(of([vehicle]));

    await TestBed.configureTestingModule({
      imports: [ZonesAdminComponent],
      providers: [
        { provide: LezApiService, useValue: lez },
        { provide: ApiService, useValue: api },
        { provide: ToastService, useValue: toast },
        { provide: ConfirmService, useValue: confirm },
        { provide: MatDialog, useValue: { open: dialogOpen } }
      ]
    })
      .overrideComponent(ZonesAdminComponent, {
        set: { imports: [ZoneMapStubComponent] }
      })
      .compileComponents();

    fixture = TestBed.createComponent(ZonesAdminComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  const buttonByText = (text: string, row = 0): HTMLButtonElement | null => {
    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    const scope: ParentNode = rows[row] ?? fixture.nativeElement;
    return Array.from(scope.querySelectorAll('button'))
      .find(button => (button.textContent ?? '').trim() === text) as HTMLButtonElement ?? null;
  };

  it('lists the templates and the branch zones', async () => {
    await setup();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Hanoi LEZ');
    expect(text).toContain('HN-LEZ');
    expect(text).toContain('Copy of Hanoi LEZ');
    expect(lez.listTemplates).toHaveBeenCalled();
    expect(lez.listZones).toHaveBeenCalled();
  });

  it('creates a LINKED zone when Enable is clicked', async () => {
    await setup([]);
    lez.createZone.and.returnValue(of(linkedZone));
    lez.listZones.and.returnValue(of([linkedZone]));

    buttonByText('Enable')?.click();
    fixture.detectChanges();

    expect(lez.createZone).toHaveBeenCalledWith({ template_id: 5, mode: 'LINKED' });
    expect(toast.success).toHaveBeenCalledWith('Zone enabled');
  });

  it('creates a COPIED zone when Copy is clicked', async () => {
    await setup([]);
    lez.createZone.and.returnValue(of(copiedZone));
    lez.listZones.and.returnValue(of([copiedZone]));

    buttonByText('Copy')?.click();
    fixture.detectChanges();

    expect(lez.createZone).toHaveBeenCalledWith({ template_id: 5, mode: 'COPIED' });
    expect(toast.success).toHaveBeenCalledWith('Zone copied');
  });

  it('surfaces the server message of a refused create (409)', async () => {
    await setup([]);
    lez.createZone.and.returnValue(throwError(() => ({
      status: 409,
      error: { errors: [{ message: 'This template is already enabled for the branch' }] }
    })));

    buttonByText('Enable')?.click();
    fixture.detectChanges();

    expect(toast.error).toHaveBeenCalledWith('This template is already enabled for the branch');
  });

  it('names the depot in the DEPOT_IN_ZONE warning', async () => {
    await setup([]);
    lez.createZone.and.returnValue(of({
      ...linkedZone,
      warnings: [{ code: 'DEPOT_IN_ZONE', depot_id: 9 }]
    }));

    buttonByText('Enable')?.click();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Central depot is inside this zone.');
  });

  it('offers Edit only on COPIED zones', async () => {
    await setup();

    const rows: NodeListOf<HTMLTableRowElement> = fixture.nativeElement
      .querySelectorAll('tbody tr');
    const linkedRow = Array.from(rows)
      .find(row => (row.textContent ?? '').includes('LINKED')) as HTMLTableRowElement;
    const copiedRow = Array.from(rows)
      .find(row => (row.textContent ?? '').includes('COPIED')) as HTMLTableRowElement;

    const editButtons = (row: HTMLTableRowElement): number =>
      Array.from(row.querySelectorAll('button'))
        .filter(button => (button.textContent ?? '').trim() === 'Edit').length;

    expect(editButtons(linkedRow)).toBe(0);
    expect(editButtons(copiedRow)).toBe(1);
  });
});
