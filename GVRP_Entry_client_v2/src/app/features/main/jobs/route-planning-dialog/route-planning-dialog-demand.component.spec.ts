import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { RoutePlanningDialogComponent } from './route-planning-dialog.component';

/** BF-C3 (BROWSER-FIX-001, C3): total demand shows at most two decimals. */
describe('RoutePlanningDialogComponent demand format', () => {
  let fixture: ComponentFixture<RoutePlanningDialogComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RoutePlanningDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: MatDialogRef, useValue: jasmine.createSpyObj('MatDialogRef', ['close']) },
        {
          provide: MAT_DIALOG_DATA,
          useValue: { selectedOrdersCount: 5, selectedVehiclesCount: 2, totalDemand: 2839.2000000000003 }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(RoutePlanningDialogComponent);
    fixture.detectChanges();
  });

  it('BF-C3: renders the float sum with no more than two decimals', () => {
    const cards = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.summary-card'));
    const demand = cards.find(card => card.textContent?.includes('Total Demand'));
    const value = demand?.querySelector('.summary-value')?.textContent?.trim() ?? '';

    expect(value).not.toContain('0000000');
    expect(value).toMatch(/^[\d,]+(\.\d{1,2})? kg$/);
    expect(value).toBe('2,839.2 kg');
  });
});
