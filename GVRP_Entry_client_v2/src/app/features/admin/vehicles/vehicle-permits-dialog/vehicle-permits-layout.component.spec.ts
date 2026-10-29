import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { BranchZoneDTO, VehicleDTO, VehiclePermitDTO } from '@core/models';
import { LezApiService } from '@core/services/lez-api.service';
import { ConfirmService } from '@shared/services/confirm.service';
import { ToastService } from '@shared/services/toast.service';

import { VehiclePermitsDialogComponent } from './vehicle-permits-dialog.component';

/**
 * BF-C5 (BROWSER-FIX-001, C6): the actions cell stays a table cell; the flex
 * layout lives on an inner div, so the delete button is not pushed out.
 */
describe('VehiclePermitsDialogComponent actions layout', () => {
  it('BF-C5: the actions td holds a .row-actions div with Edit and Delete', async () => {
    const vehicle = {
      id: 4, branch_id: 1, vehicle_license_plate: '29A-12345', vehicle_type_id: 2,
      vehicle_type_name: 'Truck 3.5t', start_depot_id: 9, end_depot_id: 9, capacity: 3500,
      status: 'AVAILABLE'
    } as VehicleDTO;
    const zone = { id: 17, mode: 'LINKED', template_id: 5, name: 'Hanoi LEZ', enabled: true, rules: [] } as BranchZoneDTO;
    const permit: VehiclePermitDTO = {
      id: 31, vehicle_id: 4, branch_zone_id: 17, permit_no: 'P-2027-001', schedule: null,
      valid_from: '2027-01-01', valid_to: '2027-12-31'
    };
    const lez = jasmine.createSpyObj<LezApiService>('LezApiService', ['listPermits', 'listZones']);
    lez.listPermits.and.returnValue(of([permit]));
    lez.listZones.and.returnValue(of([zone]));

    await TestBed.configureTestingModule({
      imports: [VehiclePermitsDialogComponent],
      providers: [
        { provide: LezApiService, useValue: lez },
        { provide: ConfirmService, useValue: jasmine.createSpyObj('ConfirmService', ['ask']) },
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['success', 'error', 'warning', 'info']) },
        { provide: MatDialogRef, useValue: { close: jasmine.createSpy('close') } },
        { provide: MAT_DIALOG_DATA, useValue: { vehicle } }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(VehiclePermitsDialogComponent);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const cell = host.querySelector('td.actions-cell');
    expect(cell).not.toBeNull();
    expect(cell?.classList.contains('row-actions')).toBeFalse();
    const actions = cell?.querySelector(':scope > div.row-actions');
    expect(actions).not.toBeNull();
    const buttons = Array.from(actions?.querySelectorAll('button') ?? []);
    expect(buttons.length).toBe(2);
    expect(buttons[0].textContent).toContain('Edit');
    expect(buttons[1].getAttribute('title')).toBe('Delete');
  });
});
