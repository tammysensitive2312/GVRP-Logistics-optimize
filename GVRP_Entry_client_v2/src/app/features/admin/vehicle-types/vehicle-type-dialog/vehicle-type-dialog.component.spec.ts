import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { VehicleTypeDTO } from '@core/models';

import { translocoTesting } from '../../../../../testing/transloco-testing';
import {
  VehicleTypeDialogComponent,
  VehicleTypeDialogData
} from './vehicle-type-dialog.component';

/** VT-T1: legal attributes in the admin vehicle-type dialog (LEZ-001 P1c). */
describe('VehicleTypeDialogComponent legal attributes', () => {
  let close: jasmine.Spy;
  let fixture: ComponentFixture<VehicleTypeDialogComponent>;
  let component: VehicleTypeDialogComponent;

  const editing: VehicleTypeDTO = {
    id: 3,
    name: 'Truck 3.5t',
    capacity: 3500,
    fixed_cost: 100000,
    cost_per_km: 5000,
    cost_per_hour: 4000,
    legal_class: 'TRUCK',
    gross_weight_kg: 3500,
    energy_type: 'DIESEL',
    emission_standard: 4,
    suggested_legal_class: 'CAR'
  };

  const setup = async (data: VehicleTypeDialogData): Promise<void> => {
    close = jasmine.createSpy('close');
    await TestBed.configureTestingModule({
      imports: [VehicleTypeDialogComponent, translocoTesting()],
      providers: [
        { provide: MatDialogRef, useValue: { close } },
        { provide: MAT_DIALOG_DATA, useValue: data }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(VehicleTypeDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  const fillRequired = (): void => {
    component.form.patchValue({
      typeName: 'Truck 1t',
      capacity: 1000,
      fixedCost: 0,
      costPerKm: 1,
      costPerHour: 1
    });
  };

  it('prefills the legal fields on edit', async () => {
    await setup({ vehicleType: editing });

    expect(component.form.controls.legalClass.value).toBe('TRUCK');
    expect(component.form.controls.grossWeightKg.value).toBe(3500);
    expect(component.form.controls.energyType.value).toBe('DIESEL');
    expect(component.form.controls.emissionStandard.value).toBe(4);
  });

  it('sends the four legal keys, null when empty, so an edit never clears them by omission', async () => {
    await setup({});
    fillRequired();

    component.onSave();

    expect(close).toHaveBeenCalledTimes(1);
    const payload = close.calls.mostRecent().args[0];
    expect(payload.legal_class).toBeNull();
    expect(payload.gross_weight_kg).toBeNull();
    expect(payload.energy_type).toBeNull();
    expect(payload.emission_standard).toBeNull();
    expect('legal_class' in payload).toBeTrue();
    expect('gross_weight_kg' in payload).toBeTrue();
    expect('energy_type' in payload).toBeTrue();
    expect('emission_standard' in payload).toBeTrue();
  });

  it('shows the suggestion and applies it only after the click', async () => {
    await setup({ vehicleType: editing });

    expect(fixture.nativeElement.textContent).toContain('Suggested: CAR');
    // Nothing is written silently.
    expect(component.form.controls.legalClass.value).toBe('TRUCK');

    component.useSuggestion();

    expect(component.form.controls.legalClass.value).toBe('CAR');
    expect(close).not.toHaveBeenCalled();
  });

  it('sends the stored legal values when editing', async () => {
    await setup({ vehicleType: editing });

    component.onSave();

    const payload = close.calls.mostRecent().args[0];
    expect(payload.legal_class).toBe('TRUCK');
    expect(payload.gross_weight_kg).toBe(3500);
    expect(payload.energy_type).toBe('DIESEL');
    expect(payload.emission_standard).toBe(4);
  });
});
