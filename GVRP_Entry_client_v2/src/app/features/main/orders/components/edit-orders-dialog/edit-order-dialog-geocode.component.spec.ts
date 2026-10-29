import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { OrderDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { ToastService } from '@shared/services/toast.service';

import { EditOrderDialogComponent } from './edit-order-dialog.component';

/** UO-C2 (UX-ORDERS-001, C1): Add Order without coordinates. */
describe('EditOrderDialogComponent optional coordinates', () => {
  let fixture: ComponentFixture<EditOrderDialogComponent>;
  let component: EditOrderDialogComponent;
  let api: jasmine.SpyObj<ApiService>;
  let toast: jasmine.SpyObj<ToastService>;

  beforeEach(async () => {
    api = jasmine.createSpyObj<ApiService>('ApiService', ['addOrder', 'updateOrder', 'getOrderById']);
    api.addOrder.and.returnValue(of({ id: 1 } as OrderDTO));
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);

    await TestBed.configureTestingModule({
      imports: [EditOrderDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: ToastService, useValue: toast },
        { provide: MatDialog, useValue: jasmine.createSpyObj<MatDialog>('MatDialog', ['open']) },
        { provide: MatDialogRef, useValue: jasmine.createSpyObj('MatDialogRef', ['close']) },
        { provide: MAT_DIALOG_DATA, useValue: {} }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(EditOrderDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    component.form = {
      ...component.form,
      order_code: 'ORD-1',
      customer_name: 'Khach',
      address: '1 Trang Tien, Ha Noi',
      demand: 10
    };
  });

  it('UO-C2: a new order starts without coordinates', () => {
    const fresh = TestBed.createComponent(EditOrderDialogComponent).componentInstance;
    expect(fresh.form.latitude).toBeNull();
    expect(fresh.form.longitude).toBeNull();
  });

  it('UO-C2: no coordinates and an address → addOrder with null coordinates and null blanks', () => {
    component.onSubmit();

    expect(api.addOrder).toHaveBeenCalledTimes(1);
    const payload = api.addOrder.calls.mostRecent().args[0] as unknown as Record<string, unknown>;
    expect(payload['latitude']).toBeNull();
    expect(payload['longitude']).toBeNull();
    expect(payload['customer_phone']).toBeNull();
    expect('status' in payload).toBeFalse();
    expect(toast.error).not.toHaveBeenCalled();
  });

  it('UO-C2: only one coordinate is an error and nothing is sent', () => {
    component.form.latitude = 21.02;

    component.onSubmit();

    expect(api.addOrder).not.toHaveBeenCalled();
    expect(toast.error).toHaveBeenCalledWith('Enter both latitude and longitude, or leave both empty.');
  });

  it('UO-C2: a latitude of 95 is an error and nothing is sent', () => {
    component.form.latitude = 95;
    component.form.longitude = 105.8;

    component.onSubmit();

    expect(api.addOrder).not.toHaveBeenCalled();
    expect(toast.error).toHaveBeenCalledWith('Latitude must be between -90 and 90');
  });

  it('UO-C2: the coordinate inputs carry no required marker and show the hint', () => {
    const host = fixture.nativeElement as HTMLElement;
    const labels = Array.from(host.querySelectorAll('label'))
      .filter(label => /Latitude|Longitude/.test(label.textContent ?? ''));

    expect(labels.length).toBe(2);
    labels.forEach(label => expect(label.querySelector('.required')).toBeNull());
    expect(host.textContent).toContain('Leave empty to locate the address automatically');
  });
});
