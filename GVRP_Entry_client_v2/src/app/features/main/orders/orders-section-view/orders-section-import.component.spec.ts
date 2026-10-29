import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';

import { ImportResultDTO, OrderDTO, PaginatedResponse } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { LezApiService } from '@core/services/lez-api.service';
import { SolutionStore } from '@core/services/solution.store';
import { ToastService } from '@shared/services/toast.service';
import { VehicleSelectionService } from '@shared/services/vehicle-selection.service';

import { ImportDialogResult } from '../components/import-orders-dialog/import-orders-dialog.component';
import { OrdersSectionComponent } from './orders-section.component';

/** UO-C4 (UX-ORDERS-001, C2): Import Orders reaches the API and reports the result. */
describe('OrdersSectionComponent import', () => {
  let fixture: ComponentFixture<OrdersSectionComponent>;
  let component: OrdersSectionComponent;
  let api: jasmine.SpyObj<ApiService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let toast: jasmine.SpyObj<ToastService>;

  const textInput: ImportDialogResult = {
    method: 'text',
    deliveryDate: '2026-10-29',
    serviceTime: 5,
    overwrite: false,
    skipValidationErrors: true,
    textData: 'ORD-1|Nguyen Van A|0901234567|1 Trang Tien, Ha Noi|25.5|08:00|11:00|1|Call first|10'
  };

  const report = (patch: Partial<ImportResultDTO>): ImportResultDTO => ({
    success: true, total_records: 3, imported_count: 2, skipped_count: 0,
    errors: [], requires_confirmation: false, ...patch
  });

  beforeEach(async () => {
    api = jasmine.createSpyObj<ApiService>('ApiService',
      ['getOrders', 'importOrders', 'bulkUpdateOrderStatus', 'submitRoutePlanningJob', 'getPareto']);
    api.getOrders.and.returnValue(of({
      content: [], total_elements: 0, total_pages: 0
    } as unknown as PaginatedResponse<OrderDTO>));
    dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    dialog.open.and.returnValue({ afterClosed: () => of(textInput) } as never);
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error', 'info']);
    const lez = jasmine.createSpyObj<LezApiService>('LezApiService', ['orderZones']);
    lez.orderZones.and.returnValue(of([]));
    const store = jasmine.createSpyObj<SolutionStore>('SolutionStore', ['loadById', 'setSolution', 'clear']);
    (store as unknown as { solution: unknown }).solution = signal(null).asReadonly();

    await TestBed.configureTestingModule({
      imports: [OrdersSectionComponent, NoopAnimationsModule],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: LezApiService, useValue: lez },
        { provide: MatDialog, useValue: dialog },
        { provide: ToastService, useValue: toast },
        { provide: VehicleSelectionService, useValue: { getSelectedIds: () => [], selectedVehicleIds$: of([]) } },
        { provide: SolutionStore, useValue: store },
        { provide: Router, useValue: jasmine.createSpyObj<Router>('Router', ['navigate']) }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(OrdersSectionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    api.getOrders.calls.reset();
  });

  it('UO-C4: sends every form field, toasts the counts and reloads the table', () => {
    api.importOrders.and.returnValue(of(report({})));

    component.onImportClick();

    const body = api.importOrders.calls.mostRecent().args[0];
    expect(body.get('textData')).toBe(textInput.textData!);
    expect(body.get('file')).toBeNull();
    expect(body.get('deliveryDate')).toBe('2026-10-29');
    expect(body.get('serviceTime')).toBe('5');
    expect(body.get('overwriteExisting')).toBe('false');
    expect(body.get('skipValidationErrors')).toBe('true');
    expect(toast.success).toHaveBeenCalledWith('Imported 2, skipped 0');
    expect(api.getOrders).toHaveBeenCalled();
  });

  it('UO-C4: a refused import shows an error toast and does not reload', () => {
    api.importOrders.and.returnValue(of(report({
      success: false, imported_count: 0,
      errors: [{ line_number: 2, order_code: 'ORD-2', field: 'demand', error_message: 'Demand is required', raw_data: 'x' }]
    })));

    component.onImportClick();

    expect(toast.error).toHaveBeenCalledWith('Import refused: 1 rows have errors. Nothing was imported.');
    expect(api.getOrders).not.toHaveBeenCalled();
  });

  it('UO-C4: the errors banner lists lines without the raw row data', () => {
    api.importOrders.and.returnValue(of(report({
      imported_count: 1, skipped_count: 1,
      errors: [{
        line_number: 2, order_code: 'ORD-2', field: 'demand',
        error_message: 'Demand must be greater than 0',
        raw_data: 'ORD-2|Secret Customer|0909999999|9 Hidden St|0'
      }]
    })));

    component.onImportClick();
    fixture.detectChanges();
    component.toggleImportDetails();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(toast.success).toHaveBeenCalledWith('Imported 1, skipped 1, 1 errors');
    expect(text).toContain('Import: 1 imported · 1 skipped · 1 errors');
    expect(text).toContain('Line 2 (ORD-2): demand — Demand must be greater than 0');
    expect(text).not.toContain('Secret Customer');
    expect(text).not.toContain('0909999999');
  });

  it('UO-C4: an HTTP error shows the server message', () => {
    api.importOrders.and.returnValue(throwError(() => ({
      message: 'Invalid data validation.',
      originalError: { error: { errors: [{ message: 'Request has no file and text input' }] } }
    })));

    component.onImportClick();

    expect(toast.error).toHaveBeenCalledWith('Request has no file and text input');
  });

  it('UO-C4: dismiss hides the banner', () => {
    api.importOrders.and.returnValue(of(report({
      errors: [{ line_number: 3, order_code: null, field: null, error_message: 'Line missing data', raw_data: null }]
    })));
    component.onImportClick();

    component.clearImportResult();
    fixture.detectChanges();

    expect(component.importResult()).toBeNull();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Import:');
  });
});
