import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { OrderDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { ToastService } from '@shared/services/toast.service';

import { EditOrderDialogComponent } from './edit-order-dialog.component';

/**
 * OF-C2 (address-only edit relocates) and OF-C3 (one time bound is refused),
 * ORDERS-FIX-001 C1/C2. ApiService is a spy; no real HTTP.
 */
describe('EditOrderDialogComponent relocate and time window', () => {
  let fixture: ComponentFixture<EditOrderDialogComponent>;
  let component: EditOrderDialogComponent;
  let api: jasmine.SpyObj<ApiService>;
  let toast: jasmine.SpyObj<ToastService>;

  const loaded = {
    id: 3, order_code: 'ORD-3', customer_name: 'Test Customer', customer_phone: '0901234567',
    address: '1 Example Street, Ha Noi', latitude: 21.0285, longitude: 105.8542,
    demand: 10, service_time: 5, priority: 2, delivery_date: '2026-10-29', status: 'SCHEDULED'
  } as unknown as OrderDTO;

  const setup = async (orderId?: number): Promise<void> => {
    api = jasmine.createSpyObj<ApiService>('ApiService', ['addOrder', 'updateOrder', 'getOrderById']);
    api.getOrderById.and.returnValue(of(loaded));
    api.updateOrder.and.returnValue(of(loaded));
    api.addOrder.and.returnValue(of(loaded));
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);

    await TestBed.configureTestingModule({
      imports: [EditOrderDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: ToastService, useValue: toast },
        { provide: MatDialog, useValue: jasmine.createSpyObj<MatDialog>('MatDialog', ['open']) },
        { provide: MatDialogRef, useValue: jasmine.createSpyObj('MatDialogRef', ['close']) },
        { provide: MAT_DIALOG_DATA, useValue: { orderId } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(EditOrderDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  const hintVisible = (): boolean =>
    ((fixture.nativeElement as HTMLElement).textContent ?? '').includes('Address changed');

  // ---------- OF-C2 ----------

  it('OF-C2: changing only the address shows the hint and sends null coordinates', async () => {
    await setup(3);
    expect(hintVisible()).toBeFalse();

    component.form = { ...component.form, address: '2 Other Street, Ha Noi' };
    fixture.detectChanges();
    expect(hintVisible()).toBeTrue();

    component.onSubmit();

    expect(api.updateOrder).toHaveBeenCalledTimes(1);
    const [id, payload] = api.updateOrder.calls.mostRecent().args;
    expect(id).toBe(3);
    const body = payload as unknown as Record<string, unknown>;
    expect(body['latitude']).toBeNull();
    expect(body['longitude']).toBeNull();
    expect(body['address']).toBe('2 Other Street, Ha Noi');
  });

  it('OF-C2: a new location picked with the new address is kept, without the hint', async () => {
    await setup(3);

    component.form = { ...component.form, address: '2 Other Street, Ha Noi', latitude: 21.04, longitude: 105.86 };
    fixture.detectChanges();
    expect(hintVisible()).toBeFalse();

    component.onSubmit();

    const body = api.updateOrder.calls.mostRecent().args[1] as unknown as Record<string, unknown>;
    expect(body['latitude']).toBe(21.04);
    expect(body['longitude']).toBe(105.86);
  });

  it('OF-C2: an unchanged address keeps the loaded coordinates', async () => {
    await setup(3);

    component.form = { ...component.form, customer_name: 'Renamed Customer' };
    component.onSubmit();

    const body = api.updateOrder.calls.mostRecent().args[1] as unknown as Record<string, unknown>;
    expect(body['latitude']).toBe(21.0285);
    expect(body['longitude']).toBe(105.8542);
  });

  it('OF-C2: the hint never shows when adding an order', async () => {
    await setup();

    component.form = { ...component.form, address: '2 Other Street, Ha Noi' };
    fixture.detectChanges();

    expect(component.showStaleCoordinateHint).toBeFalse();
    expect(hintVisible()).toBeFalse();
  });

  // ---------- OF-C3 ----------

  it('OF-C3: only a start time on edit is an error and nothing is sent', async () => {
    await setup(3);

    component.form = { ...component.form, time_window_start: '08:00', time_window_end: '' };
    component.onSubmit();

    expect(toast.error).toHaveBeenCalledWith('Enter both start and end time, or leave both empty.');
    expect(api.updateOrder).not.toHaveBeenCalled();
    expect(api.addOrder).not.toHaveBeenCalled();
  });

  it('OF-C3: only an end time on create is an error and nothing is sent', async () => {
    await setup();

    component.form = {
      ...component.form,
      order_code: 'ORD-9', customer_name: 'Test Customer', address: '1 Example Street, Ha Noi',
      demand: 10, time_window_start: '', time_window_end: '11:00'
    };
    component.onSubmit();

    expect(toast.error).toHaveBeenCalledWith('Enter both start and end time, or leave both empty.');
    expect(api.addOrder).not.toHaveBeenCalled();
    expect(api.updateOrder).not.toHaveBeenCalled();
  });
});
