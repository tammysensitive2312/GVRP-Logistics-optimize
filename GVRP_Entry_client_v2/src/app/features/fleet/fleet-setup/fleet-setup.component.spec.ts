import { ComponentFixture, TestBed } from '@angular/core/testing';

import { translocoTesting } from '../../../../testing/transloco-testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { DepotDTO, FleetDTO, VehicleTypeDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { DepotStore } from '@core/services/depot.store';
import { VehicleTypeStore } from '@core/services/vehicle-type.store';
import { ConfirmService } from '@shared/services/confirm.service';
import { ToastService } from '@shared/services/toast.service';

import { FleetSetupComponent } from './fleet-setup.component';

describe('FleetSetupComponent', () => {
  let fixture: ComponentFixture<FleetSetupComponent>;
  let component: FleetSetupComponent;

  let api: jasmine.SpyObj<Pick<ApiService, 'createFleet'>>;
  let depotStore: jasmine.SpyObj<Pick<DepotStore, 'load'>>;
  let vehicleTypeStore: jasmine.SpyObj<Pick<VehicleTypeStore, 'load'>>;
  let confirm: jasmine.SpyObj<ConfirmService>;
  let toast: jasmine.SpyObj<ToastService>;
  let router: jasmine.SpyObj<Router>;

  const depot: DepotDTO = {
    id: 1,
    name: 'Kho Hà Nội',
    address: '1 Đinh Tiên Hoàng',
    latitude: 21.028511,
    longitude: 105.804817,
    branch_id: 1,
    created_at: '',
    updated_at: ''
  };

  const vehicleType: VehicleTypeDTO = {
    id: 3,
    name: 'Xe tải 5 tấn',
    capacity: 5000,
    fixed_cost: 50000,
    cost_per_km: 5000,
    cost_per_hour: 4000
  };

  const createdFleet: FleetDTO = {
    id: 11,
    fleet_name: 'Đội nội thành',
    branch_id: 1,
    vehicle_count: 1
  };

  const setDepots = (depots: readonly DepotDTO[]) =>
    Object.defineProperty(depotStore, 'depots', { value: () => depots, writable: true });

  const setVehicleTypes = (types: readonly VehicleTypeDTO[]) =>
    Object.defineProperty(vehicleTypeStore, 'vehicleTypes', {
      value: () => types,
      writable: true
    });

  const createComponent = () => {
    fixture = TestBed.createComponent(FleetSetupComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  const fillFirstRow = () => {
    component.form.controls.fleetName.setValue('Đội nội thành');
    component.form.controls.vehicles.at(0).patchValue({
      licensePlate: ' 29A-12345 ',
      vehicleTypeId: vehicleType.id,
      startDepotId: depot.id,
      endDepotId: depot.id
    });
  };

  beforeEach(async () => {
    api = jasmine.createSpyObj<Pick<ApiService, 'createFleet'>>('ApiService', ['createFleet']);
    depotStore = jasmine.createSpyObj<Pick<DepotStore, 'load'>>('DepotStore', ['load']);
    vehicleTypeStore = jasmine.createSpyObj<Pick<VehicleTypeStore, 'load'>>(
      'VehicleTypeStore',
      ['load']
    );
    confirm = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['ask']);
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error', 'info']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);

    depotStore.load.and.returnValue(of([depot]));
    vehicleTypeStore.load.and.returnValue(of([vehicleType]));
    setDepots([depot]);
    setVehicleTypes([vehicleType]);
    router.navigate.and.resolveTo(true);
    confirm.ask.and.returnValue(of(true));

    await TestBed.configureTestingModule({
      imports: [FleetSetupComponent, translocoTesting()],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: DepotStore, useValue: depotStore },
        { provide: VehicleTypeStore, useValue: vehicleTypeStore },
        { provide: ConfirmService, useValue: confirm },
        { provide: ToastService, useValue: toast },
        { provide: Router, useValue: router }
      ]
    }).compileComponents();
  });

  it('starts with exactly one vehicle row, like V1', () => {
    createComponent();

    expect(component.totalVehicles()).toBe(1);
  });

  it('bounces back to depot setup when no depot exists', () => {
    depotStore.load.and.returnValue(of([]));

    createComponent();

    expect(toast.error).toHaveBeenCalledWith(
      'fleetSetup.noDepots'
    );
    expect(router.navigate).toHaveBeenCalledWith(['/setup/depot']);
    expect(component.totalVehicles()).toBe(0);
  });

  it('bounces back to vehicle types when none exist', () => {
    vehicleTypeStore.load.and.returnValue(of([]));

    createComponent();

    expect(toast.error).toHaveBeenCalledWith(
      'fleetSetup.noVehicleTypes'
    );
    expect(router.navigate).toHaveBeenCalledWith(['/setup/vehicle-types']);
  });

  it('refuses to remove the last vehicle', () => {
    createComponent();

    component.removeVehicle(0);

    expect(toast.error).toHaveBeenCalledWith('fleetSetup.atLeastOneVehicle');
    expect(component.totalVehicles()).toBe(1);
    expect(confirm.ask).not.toHaveBeenCalled();
  });

  it('removes a vehicle after confirmation', () => {
    createComponent();
    component.addVehicle();

    component.removeVehicle(1);

    expect(confirm.ask).toHaveBeenCalled();
    expect(component.totalVehicles()).toBe(1);
    expect(toast.success).toHaveBeenCalledWith('fleetSetup.removed');
  });

  it('keeps the vehicle when the confirmation is dismissed', () => {
    confirm.ask.and.returnValue(of(false));
    createComponent();
    component.addVehicle();

    component.removeVehicle(1);

    expect(component.totalVehicles()).toBe(2);
  });

  it('reports the first per-vehicle error with the V1 "Xe #n" prefix', () => {
    createComponent();
    component.form.controls.fleetName.setValue('Đội nội thành');

    component.onSubmit();

    expect(api.createFleet).not.toHaveBeenCalled();
    expect(toast.error).toHaveBeenCalledWith('fleetSetup.errors.plateRequired');
  });

  it('reports a missing fleet name before any vehicle error', () => {
    createComponent();

    component.onSubmit();

    expect(toast.error).toHaveBeenCalledWith('fleetSetup.errors.nameRequired');
  });

  it('posts a trimmed payload and goes to /main on success', () => {
    api.createFleet.and.returnValue(of(createdFleet));
    createComponent();
    fillFirstRow();

    component.onSubmit();

    expect(api.createFleet).toHaveBeenCalledWith({
      fleet_name: 'Đội nội thành',
      vehicles: [
        {
          vehicle_license_plate: '29A-12345',
          vehicle_type_id: vehicleType.id,
          start_depot_id: depot.id,
          end_depot_id: depot.id
        }
      ]
    });
    expect(toast.success).toHaveBeenCalledWith('fleetSetup.created');
    expect(router.navigate).toHaveBeenCalledWith(['/main']);
  });

  it('keeps the form and surfaces the error when create fails', () => {
    api.createFleet.and.returnValue(throwError(() => ({ message: 'plate exists' })));
    createComponent();
    fillFirstRow();

    component.onSubmit();

    expect(toast.error).toHaveBeenCalledWith('plate exists');
    expect(component.saving()).toBeFalse();
    expect(component.form.controls.fleetName.value).toBe('Đội nội thành');
  });

  it('exposes the selected vehicle type for the info line', () => {
    createComponent();
    fillFirstRow();

    const row = component.form.controls.vehicles.at(0);
    expect(component.selectedTypeInfo(row)).toEqual(vehicleType);

    row.controls.vehicleTypeId.setValue(null);
    expect(component.selectedTypeInfo(row)).toBeNull();
  });
});
