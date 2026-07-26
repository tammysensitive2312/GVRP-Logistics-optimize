import { ComponentFixture, TestBed } from '@angular/core/testing';

import { translocoTesting } from '../../../../testing/transloco-testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { VehicleTypeDTO, VehicleTypeInputDTO } from '@core/models';
import { VehicleTypeStore } from '@core/services/vehicle-type.store';
import { ToastService } from '@shared/services/toast.service';

import { VehicleTypeSetupComponent } from './vehicle-type-setup.component';

describe('VehicleTypeSetupComponent', () => {
  let fixture: ComponentFixture<VehicleTypeSetupComponent>;
  let component: VehicleTypeSetupComponent;

  let store: jasmine.SpyObj<Pick<VehicleTypeStore, 'load' | 'create'>>;
  let toast: jasmine.SpyObj<ToastService>;
  let router: jasmine.SpyObj<Router>;

  const created: VehicleTypeDTO = {
    id: 7,
    name: 'Xe tải 5 tấn',
    capacity: 5000,
    fixed_cost: 50000,
    cost_per_km: 5000,
    cost_per_hour: 4000
  };

  const fillValidForm = () => {
    component.form.patchValue({
      typeName: '  Xe tải 5 tấn  ',
      capacity: 5000,
      fixedCost: 50000,
      costPerKm: 5000,
      costPerHour: 4000
    });
  };

  beforeEach(async () => {
    store = jasmine.createSpyObj<Pick<VehicleTypeStore, 'load' | 'create'>>(
      'VehicleTypeStore',
      ['load', 'create']
    );
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error', 'info']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);

    store.load.and.returnValue(of([]));
    router.navigate.and.resolveTo(true);

    // hasVehicleTypes is a computed signal on the real store; stub it as a getter.
    Object.defineProperty(store, 'vehicleTypes', { value: () => [], writable: true });
    Object.defineProperty(store, 'loading', { value: () => false, writable: true });
    Object.defineProperty(store, 'hasVehicleTypes', { value: () => false, writable: true });

    await TestBed.configureTestingModule({
      imports: [VehicleTypeSetupComponent, translocoTesting()],
      providers: [
        { provide: VehicleTypeStore, useValue: store },
        { provide: ToastService, useValue: toast },
        { provide: Router, useValue: router }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(VehicleTypeSetupComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads the existing vehicle types on init', () => {
    expect(store.load).toHaveBeenCalled();
  });

  it('starts invalid and reports the V1 message for a missing name', () => {
    component.onSubmit();

    expect(store.create).not.toHaveBeenCalled();
    expect(toast.error).toHaveBeenCalledWith('vehicleTypeSetup.errors.nameRequired');
  });

  it('rejects a non-positive capacity', () => {
    fillValidForm();
    component.form.controls.capacity.setValue(0);

    component.onSubmit();

    expect(store.create).not.toHaveBeenCalled();
    expect(toast.error).toHaveBeenCalledWith('vehicleTypeSetup.errors.capacityPositive');
  });

  it('rejects negative costs', () => {
    fillValidForm();
    component.form.controls.fixedCost.setValue(-1);

    component.onSubmit();

    expect(toast.error).toHaveBeenCalledWith('vehicleTypeSetup.errors.fixedCostNonNegative');
  });

  it('accepts zero costs, as V1 did', () => {
    store.create.and.returnValue(of(created));
    fillValidForm();
    component.form.controls.fixedCost.setValue(0);

    component.onSubmit();

    expect(store.create).toHaveBeenCalled();
  });

  it('rejects a non-positive max distance only when provided', () => {
    store.create.and.returnValue(of(created));
    fillValidForm();

    component.form.controls.maxDistance.setValue(0);
    component.onSubmit();
    expect(toast.error).toHaveBeenCalledWith('vehicleTypeSetup.errors.maxDistancePositive');

    component.form.controls.maxDistance.setValue(null);
    component.onSubmit();
    expect(store.create).toHaveBeenCalled();
  });

  it('builds the V1 payload shape: type_name, trimmed, with vehicle_features', () => {
    store.create.and.returnValue(of(created));

    fillValidForm();
    component.form.controls.emissionFactor.setValue(12.3);
    component.form.controls.maxDuration.setValue(480);

    component.onSubmit();

    const expected: VehicleTypeInputDTO = {
      type_name: 'Xe tải 5 tấn',
      vehicle_features: { emission_factor: 12.3 },
      capacity: 5000,
      fixed_cost: 50000,
      cost_per_km: 5000,
      cost_per_hour: 4000,
      max_duration: 480
    };

    expect(store.create).toHaveBeenCalledWith(expected);
    expect(toast.success).toHaveBeenCalledWith('vehicleTypeSetup.created');
  });

  it('omits optional keys and sends an empty feature bag when unset', () => {
    store.create.and.returnValue(of(created));
    fillValidForm();

    component.onSubmit();

    expect(store.create).toHaveBeenCalledWith({
      type_name: 'Xe tải 5 tấn',
      vehicle_features: {},
      capacity: 5000,
      fixed_cost: 50000,
      cost_per_km: 5000,
      cost_per_hour: 4000
    });
  });

  it('resets the form after a successful create and stays on the screen', () => {
    store.create.and.returnValue(of(created));
    fillValidForm();

    component.onSubmit();

    expect(component.form.controls.typeName.value).toBe('');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('keeps the values and surfaces the error when create fails', () => {
    store.create.and.returnValue(throwError(() => ({ message: 'duplicate name' })));
    fillValidForm();

    component.onSubmit();

    expect(toast.error).toHaveBeenCalledWith('duplicate name');
    expect(component.form.controls.capacity.value).toBe(5000);
    expect(component.saving()).toBeFalse();
  });

  it('does not continue to fleet setup while no vehicle type exists', () => {
    component.onContinue();

    expect(router.navigate).not.toHaveBeenCalled();
  });
});
