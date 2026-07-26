import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { DepotDTO, DepotInputDTO } from '@core/models';
import { DepotStore } from '@core/services/depot.store';
import { GeocodingService } from '@shared/services/geocoding.service';
import { ToastService } from '@shared/services/toast.service';

import { DepotSetupComponent } from './depot-setup.component';

describe('DepotSetupComponent', () => {
  let fixture: ComponentFixture<DepotSetupComponent>;
  let component: DepotSetupComponent;

  let depotStore: jasmine.SpyObj<Pick<DepotStore, 'create'>>;
  let geocoding: jasmine.SpyObj<GeocodingService>;
  let toast: jasmine.SpyObj<ToastService>;
  let router: jasmine.SpyObj<Router>;

  const LOCATION = { latitude: 21.028511, longitude: 105.804817 };

  const createdDepot: DepotDTO = {
    id: 1,
    name: 'Kho Hà Nội',
    address: '1 Đinh Tiên Hoàng, Hà Nội',
    latitude: LOCATION.latitude,
    longitude: LOCATION.longitude,
    branch_id: 1,
    created_at: '2026-01-01T00:00:00Z',
    updated_at: '2026-01-01T00:00:00Z'
  };

  beforeEach(async () => {
    depotStore = jasmine.createSpyObj<Pick<DepotStore, 'create'>>('DepotStore', ['create']);
    geocoding = jasmine.createSpyObj<GeocodingService>('GeocodingService', [
      'reverseGeocode',
      'formatCoordinates'
    ]);
    toast = jasmine.createSpyObj<ToastService>('ToastService', [
      'success',
      'error',
      'info'
    ]);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);

    geocoding.reverseGeocode.and.returnValue(of('1 Đinh Tiên Hoàng, Hà Nội'));
    geocoding.formatCoordinates.and.returnValue('21.028511, 105.804817');
    router.navigate.and.resolveTo(true);

    await TestBed.configureTestingModule({
      imports: [DepotSetupComponent],
      providers: [
        { provide: DepotStore, useValue: depotStore },
        { provide: GeocodingService, useValue: geocoding },
        { provide: ToastService, useValue: toast },
        { provide: Router, useValue: router }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DepotSetupComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('starts invalid: no name and no location picked', () => {
    expect(component.form.invalid).toBeTrue();
    expect(component.form.hasError('requiredLocation')).toBeTrue();
  });

  it('fills coordinates and reverse-geocoded address when a location is picked', () => {
    component.onLocationPicked(LOCATION);

    expect(component.form.controls.latitude.value).toBe(LOCATION.latitude);
    expect(component.form.controls.longitude.value).toBe(LOCATION.longitude);
    expect(geocoding.reverseGeocode).toHaveBeenCalledWith(
      LOCATION.latitude,
      LOCATION.longitude
    );
    expect(component.form.controls.address.value).toBe('1 Đinh Tiên Hoàng, Hà Nội');
  });

  it('blocks submit and reports the V1 message when the name is missing', () => {
    component.onLocationPicked(LOCATION);

    component.onSubmit();

    expect(depotStore.create).not.toHaveBeenCalled();
    expect(toast.error).toHaveBeenCalledWith('Vui lòng nhập tên depot');
  });

  it('blocks submit when no location was picked', () => {
    component.form.controls.name.setValue('Kho Hà Nội');

    component.onSubmit();

    expect(depotStore.create).not.toHaveBeenCalled();
    expect(toast.error).toHaveBeenCalledWith('Vui lòng chọn vị trí trên bản đồ');
  });

  it('rejects a whitespace-only name', () => {
    component.form.controls.name.setValue('   ');
    component.onLocationPicked(LOCATION);

    component.onSubmit();

    expect(depotStore.create).not.toHaveBeenCalled();
    expect(toast.error).toHaveBeenCalledWith('Vui lòng nhập tên depot');
  });

  it('posts a trimmed payload and navigates to the next setup step on success', () => {
    depotStore.create.and.returnValue(of(createdDepot));

    component.form.controls.name.setValue('  Kho Hà Nội  ');
    component.onLocationPicked(LOCATION);

    component.onSubmit();

    const expected: DepotInputDTO = {
      name: 'Kho Hà Nội',
      address: '1 Đinh Tiên Hoàng, Hà Nội',
      latitude: LOCATION.latitude,
      longitude: LOCATION.longitude
    };

    expect(depotStore.create).toHaveBeenCalledWith(expected);
    expect(toast.success).toHaveBeenCalledWith('Depot đã được tạo thành công!');
    expect(router.navigate).toHaveBeenCalledWith(['/setup/vehicle-types']);
    expect(component.saving()).toBeFalse();
  });

  it('keeps the form open and shows the error when the API fails', () => {
    depotStore.create.and.returnValue(
      throwError(() => ({ message: 'Depot name already exists', status: 409 }))
    );

    component.form.controls.name.setValue('Kho Hà Nội');
    component.onLocationPicked(LOCATION);

    component.onSubmit();

    expect(toast.success).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
    expect(toast.error).toHaveBeenCalledWith('Depot name already exists');
    expect(component.saving()).toBeFalse();
    expect(component.form.controls.name.value).toBe('Kho Hà Nội');
  });

  it('clears the form and the picked marker on reset', () => {
    component.form.controls.name.setValue('Kho Hà Nội');
    component.onLocationPicked(LOCATION);

    component.onReset();

    expect(component.form.controls.name.value).toBe('');
    expect(component.picked()).toBeNull();
    expect(component.pickedLatitude()).toBeNull();
    expect(toast.info).toHaveBeenCalledWith('Form đã được reset');
  });
});
