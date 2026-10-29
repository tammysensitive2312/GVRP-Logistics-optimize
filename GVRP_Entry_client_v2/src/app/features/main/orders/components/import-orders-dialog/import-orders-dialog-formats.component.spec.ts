import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialogRef } from '@angular/material/dialog';

import { localDateIso } from '@shared/utils/date.utils';

import { ImportOrdersDialogComponent, ORDER_IMPORT_TEMPLATE_CSV } from './import-orders-dialog.component';

/** UO-C5 (UX-ORDERS-001, C3): formats, default date and template of the import dialog. */
describe('ImportOrdersDialogComponent formats', () => {
  let fixture: ComponentFixture<ImportOrdersDialogComponent>;
  let component: ImportOrdersDialogComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ImportOrdersDialogComponent, NoopAnimationsModule],
      providers: [{ provide: MatDialogRef, useValue: jasmine.createSpyObj('MatDialogRef', ['close']) }]
    }).compileComponents();

    fixture = TestBed.createComponent(ImportOrdersDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  const select = (name: string): void => {
    component.onFileSelected({ target: { files: [new File(['x'], name)] } } as unknown as Event);
  };

  it('UO-C5: an .xlsx file is rejected', () => {
    select('orders.xlsx');

    expect(component.selectedFile).toBeNull();
    expect(component.fileError).toBe('Only CSV and JSON files are allowed');
  });

  it('UO-C5: .json and .csv files are accepted', () => {
    select('orders.json');
    expect(component.selectedFile?.name).toBe('orders.json');
    expect(component.fileError).toBe('');

    select('orders.csv');
    expect(component.selectedFile?.name).toBe('orders.csv');
  });

  it('UO-C5: the default delivery date is the local today', () => {
    expect(component.deliveryDate).toBe(localDateIso());
  });

  it('UO-C5: the template has the 13 columns the server reads and two example rows', () => {
    const lines = ORDER_IMPORT_TEMPLATE_CSV.trim().split('\n');

    expect(lines[0].split(',')).toEqual([
      'orderCode', 'customerName', 'customerPhone', 'address', 'latitude', 'longitude',
      'demand', 'serviceTime', 'timeWindowStart', 'timeWindowEnd', 'priority',
      'deliveryNotes', 'requiredSkills'
    ]);
    expect(lines.length).toBe(3);
    expect(lines[1]).toContain('21.0245,105.8572');
    expect(lines[1]).toContain('REFRIGERATED;FRAGILE_CAPABLE');
    expect(lines[2]).toContain(',,,');
    expect(lines[2]).toContain('"2 Hang Bai, Hoan Kiem, Ha Noi"');
  });

  it('UO-C5: the file input accepts CSV and JSON only, and the text hint lists 10 fields', () => {
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('input[type="file"]')?.getAttribute('accept')).toBe('.csv,.json');

    component.method = 'text';
    fixture.detectChanges();

    expect(host.textContent).toContain('Format (10 fields):');
    expect(host.textContent).toContain('|ServiceTime');
    expect(host.textContent).toContain('Coordinates are found from the address automatically.');
  });
});
