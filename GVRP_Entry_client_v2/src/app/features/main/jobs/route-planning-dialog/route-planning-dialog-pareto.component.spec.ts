import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { RoutePlanningDialogComponent } from './route-planning-dialog.component';

/** PUI-007: the planning dialog warns that Pareto runs five scenarios. */
describe('RoutePlanningDialogComponent Pareto hint', () => {
  let fixture: ComponentFixture<RoutePlanningDialogComponent>;
  let component: RoutePlanningDialogComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RoutePlanningDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: MatDialogRef, useValue: jasmine.createSpyObj('MatDialogRef', ['close']) },
        {
          provide: MAT_DIALOG_DATA,
          useValue: { selectedOrdersCount: 5, selectedVehiclesCount: 2, totalDemand: 100 }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(RoutePlanningDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('keeps the plain estimate when Pareto is off', () => {
    const host = fixture.nativeElement as HTMLElement;
    expect(host.textContent).not.toContain('× 5 scenarios');
    expect(host.textContent).toContain('Generate multiple solutions for trade-off analysis');
  });

  it('shows the five-scenario estimate and hint when Pareto is enabled', () => {
    component.form.enablePareto = true;
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.textContent).toContain('× 5 scenarios (Pareto)');
    expect(host.textContent)
      .toContain('Runs 5 weighted scenarios and lets you switch between the best trade-offs.');
  });
});
