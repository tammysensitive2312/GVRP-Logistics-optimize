import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatSelect } from '@angular/material/select';
import { By } from '@angular/platform-browser';
import { of } from 'rxjs';

import { ApiService } from '@core/services/api.service';

import { SendRouteDialogComponent } from './send-route-dialog.component';

/** U-T8: the revision picker labels PARETO_ALTERNATIVE, other types stay raw. */
describe('SendRouteDialogComponent Pareto revision label', () => {
  let fixture: ComponentFixture<SendRouteDialogComponent>;
  let component: SendRouteDialogComponent;
  let api: jasmine.SpyObj<ApiService>;

  const drivers = [{ id: 2, username: 'taylai', full_name: 'Tai Lai', phone: '0901' }];

  async function setup(revisionType: string): Promise<void> {
    api = jasmine.createSpyObj<ApiService>('ApiService',
      ['listDrivers', 'revisionChain', 'routeSends']);
    api.listDrivers.and.returnValue(of(drivers));
    api.revisionChain.and.returnValue(of([
      { id: 9, created_at: '2026-10-18T08:00:00', type: revisionType, status: 'SUCCESS' }
    ]));
    api.routeSends.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [SendRouteDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: MatDialogRef, useValue: jasmine.createSpyObj('MatDialogRef', ['close']) },
        {
          provide: MAT_DIALOG_DATA,
          useValue: { routeId: 21, solutionId: 9, licensePlate: '29A-1' }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SendRouteDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  /** Text of the "Plan revision" dropdown once opened. */
  async function revisionOptionText(): Promise<string> {
    const planSelect = fixture.debugElement.queryAll(By.directive(MatSelect))[1]
      .componentInstance as MatSelect;
    planSelect.open();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const text = Array.from(document.querySelectorAll('mat-option'))
      .map(option => (option.textContent ?? '').replace(/\s+/g, ' ').trim())
      .join(' | ');
    planSelect.close();
    fixture.detectChanges();
    return text;
  }

  it('labels PARETO_ALTERNATIVE revisions as "Pareto plan"', async () => {
    await setup('PARETO_ALTERNATIVE');

    expect(component.typeLabel('PARETO_ALTERNATIVE')).toBe('Pareto plan');
    expect(await revisionOptionText()).toContain('Pareto plan');
  });

  it('keeps other revision types unchanged', async () => {
    await setup('ENGINE_GENERATED');

    expect(component.typeLabel('ENGINE_GENERATED')).toBe('ENGINE_GENERATED');
    const text = await revisionOptionText();
    expect(text).toContain('ENGINE_GENERATED');
    expect(text).not.toContain('Pareto plan');
  });
});
