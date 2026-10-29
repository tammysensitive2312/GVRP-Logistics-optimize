import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';

import { OrderDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { ToastService } from '@shared/services/toast.service';
import { localDateIso } from '@shared/utils/date.utils';

import { EditOrderDialogComponent } from './edit-order-dialog.component';

/** BF-C1 (BROWSER-FIX-001, C1): local default date and the server's error text. */
describe('EditOrderDialogComponent errors and default date', () => {
  let fixture: ComponentFixture<EditOrderDialogComponent>;
  let component: EditOrderDialogComponent;
  let api: jasmine.SpyObj<ApiService>;
  let toast: jasmine.SpyObj<ToastService>;

  // The shape thrown by errorInterceptor, carrying the API's 400 body.
  const interceptorError = {
    message: 'Invalid data validation.',
    status: 400,
    originalError: { error: { errors: [{ code: '0040001', message: 'Order code is required', field: 'orderCode' }] } }
  };

  const setup = async (orderId?: number): Promise<void> => {
    api = jasmine.createSpyObj<ApiService>('ApiService', ['addOrder', 'updateOrder', 'getOrderById']);
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);
    api.getOrderById.and.returnValue(of({
      id: 3, order_code: 'ORD-3', customer_name: 'Khach', address: 'Hanoi',
      latitude: 21, longitude: 105, demand: 10, service_time: 5, priority: 5,
      delivery_date: '2026-10-29', status: 'SCHEDULED'
    } as unknown as OrderDTO));

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

  const fillValidForm = (): void => {
    component.form = {
      ...component.form,
      order_code: 'ORD-1',
      customer_name: 'Khach',
      address: 'Hanoi',
      latitude: 21,
      longitude: 105,
      demand: 10
    };
  };

  it('BF-C1: the default delivery date is the local today', async () => {
    await setup();

    expect(component.form.delivery_date).toBe(localDateIso());
  });

  it('BF-C1: a failed create shows the server message', async () => {
    await setup();
    api.addOrder.and.returnValue(throwError(() => interceptorError));
    fillValidForm();

    component.onSubmit();

    expect(toast.error).toHaveBeenCalledWith('Order code is required');
    expect(component.isSaving).toBeFalse();
  });

  it('BF-C1: a failed update shows the server message', async () => {
    await setup(3);
    api.updateOrder.and.returnValue(throwError(() => interceptorError));

    component.onSubmit();

    expect(api.updateOrder).toHaveBeenCalled();
    expect(toast.error).toHaveBeenCalledWith('Order code is required');
  });

  it('BF-C1: an error without a body falls back to the local text', async () => {
    await setup();
    api.addOrder.and.returnValue(throwError(() => ({})));
    fillValidForm();

    component.onSubmit();

    expect(toast.error).toHaveBeenCalledWith('Failed to create order');
  });
});
