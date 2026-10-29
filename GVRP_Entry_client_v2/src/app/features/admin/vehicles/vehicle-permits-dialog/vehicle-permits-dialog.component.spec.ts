import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { BranchZoneDTO, VehicleDTO, VehiclePermitDTO } from '@core/models';
import { LezApiService } from '@core/services/lez-api.service';
import { ConfirmService } from '@shared/services/confirm.service';
import { ToastService } from '@shared/services/toast.service';

import { VehiclePermitsDialogComponent } from './vehicle-permits-dialog.component';

/** VP-T1: whole-day vs interval permits, and delete only after confirmation. */
describe('VehiclePermitsDialogComponent', () => {
  let fixture: ComponentFixture<VehiclePermitsDialogComponent>;
  let component: VehiclePermitsDialogComponent;
  let lez: jasmine.SpyObj<LezApiService>;
  let confirm: jasmine.SpyObj<ConfirmService>;
  let toast: jasmine.SpyObj<ToastService>;

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

  const zone: BranchZoneDTO = {
    id: 17,
    mode: 'LINKED',
    template_id: 5,
    name: 'Hanoi LEZ',
    enabled: true,
    rules: []
  };

  const permit: VehiclePermitDTO = {
    id: 31,
    vehicle_id: 4,
    branch_zone_id: 17,
    permit_no: 'P-2027-001',
    schedule: null,
    // A stored permit always has both bounds: the columns are NOT NULL
    // (review-p1c R5).
    valid_from: '2027-01-01',
    valid_to: '2027-12-31'
  };

  const setup = async (): Promise<void> => {
    lez = jasmine.createSpyObj<LezApiService>('LezApiService', [
      'listPermits', 'listZones', 'createPermit', 'updatePermit', 'deletePermit'
    ]);
    confirm = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['ask']);
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error', 'warning', 'info']);

    lez.listPermits.and.returnValue(of([permit]));
    lez.listZones.and.returnValue(of([zone]));
    lez.createPermit.and.returnValue(of(permit));
    lez.updatePermit.and.returnValue(of(permit));
    lez.deletePermit.and.returnValue(of(void 0));

    await TestBed.configureTestingModule({
      imports: [VehiclePermitsDialogComponent],
      providers: [
        { provide: LezApiService, useValue: lez },
        { provide: ConfirmService, useValue: confirm },
        { provide: ToastService, useValue: toast },
        { provide: MatDialogRef, useValue: { close: jasmine.createSpy('close') } },
        { provide: MAT_DIALOG_DATA, useValue: { vehicle } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(VehiclePermitsDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  it('loads the permits of the vehicle and its zones', async () => {
    await setup();

    expect(lez.listPermits).toHaveBeenCalledWith(4);
    expect(component.permits().length).toBe(1);
    expect(component.zoneName(17)).toBe('Hanoi LEZ');
    expect(component.scheduleText(permit)).toBe('Whole day');
  });

  it('sends schedule: null for a whole-day permit', async () => {
    await setup();

    component.startCreate();
    component.zoneId.set(17);
    component.permitNo.set('P-2027-002');
    component.wholeDay.set(true);

    component.save();

    expect(lez.createPermit).toHaveBeenCalledWith(4, jasmine.objectContaining({
      branch_zone_id: 17,
      permit_no: 'P-2027-002',
      schedule: null
    }));
    const payload = lez.createPermit.calls.mostRecent().args[1];
    expect(payload.schedule).toBeNull();
  });

  it('sends ALLOWED_ONLY with the intervals for a "only during" permit', async () => {
    await setup();

    component.startCreate();
    component.zoneId.set(17);
    component.permitNo.set('P-2027-003');
    component.wholeDay.set(false);
    component.intervals.set([{ start: '06:00', end: '09:00' }]);

    component.save();

    const payload = lez.createPermit.calls.mostRecent().args[1];
    expect(payload.schedule).toEqual({
      type: 'ALLOWED_ONLY',
      intervals: [['06:00', '09:00']]
    });
  });

  it('updates an existing permit instead of creating a new one', async () => {
    await setup();

    component.startEdit(permit);
    component.permitNo.set('P-2027-001-b');

    component.save();

    expect(lez.updatePermit).toHaveBeenCalledWith(4, 31, jasmine.objectContaining({
      permit_no: 'P-2027-001-b'
    }));
    expect(lez.createPermit).not.toHaveBeenCalled();
  });

  it('deletes only after the confirmation', async () => {
    await setup();
    confirm.ask.and.returnValue(of(false));

    component.remove(permit);

    expect(confirm.ask).toHaveBeenCalled();
    expect(lez.deletePermit).not.toHaveBeenCalled();

    confirm.ask.and.returnValue(of(true));
    component.remove(permit);

    expect(lez.deletePermit).toHaveBeenCalledWith(4, 31);
    expect(toast.success).toHaveBeenCalledWith('Permit deleted');
  });

  it('blocks a save without a permit number', async () => {
    await setup();

    component.startCreate();
    component.zoneId.set(17);
    component.permitNo.set('  ');

    component.save();

    expect(component.formErrors()).toContain('Permit number is required.');
    expect(lez.createPermit).not.toHaveBeenCalled();
  });

  // FR5-T1 (review-p1c R5): an edit may not clear a validity bound either.
  it('requires both validity bounds when editing an existing permit', async () => {
    await setup();

    component.startEdit(permit);
    component.validTo.set('');

    component.save();

    expect(component.formErrors()).toContain('"Valid to" is required.');
    expect(lez.updatePermit).not.toHaveBeenCalled();
    expect(lez.createPermit).not.toHaveBeenCalled();
  });
});
