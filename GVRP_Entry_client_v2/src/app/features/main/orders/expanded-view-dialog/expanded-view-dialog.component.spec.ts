import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';

import { SolutionDTO } from '@core/models';

import { ExpandedViewDialogComponent, ExpandedViewDialogData } from './expanded-view-dialog.component';

@Component({ selector: 'app-compliance-panel', standalone: true, template: '<div class="panel-stub"></div>' })
class CompliancePanelStubComponent {
  @Input() solution: unknown;
  @Output() complianceChange = new EventEmitter<unknown>();
  @Output() violationFocused = new EventEmitter<void>();
  recheck = jasmine.createSpy('recheck');
}

@Component({ selector: 'app-route-view', standalone: true, template: '<div class="route-stub"></div>' })
class RouteViewStubComponent {
  @Input() solution: unknown;
  @Input() compliance: unknown;
  @Output() complianceRecheckNeeded = new EventEmitter<void>();
}

@Component({ selector: 'app-timeline-view', standalone: true, template: '<div class="timeline-stub"></div>' })
class TimelineViewStubComponent {
  @Input() solution: unknown;
}

/** UL-T9 (UX-LEZ-001, D8): the expanded Route / Timeline dialog. */
describe('ExpandedViewDialogComponent', () => {
  let fixture: ComponentFixture<ExpandedViewDialogComponent>;
  let close: jasmine.Spy;

  const solution = { id: 9, routes: [] } as unknown as SolutionDTO;

  const setup = async (view: ExpandedViewDialogData['view']): Promise<void> => {
    close = jasmine.createSpy('close');
    await TestBed.configureTestingModule({
      imports: [ExpandedViewDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: MatDialogRef, useValue: { close } },
        { provide: MAT_DIALOG_DATA, useValue: { view, solution } }
      ]
    })
      .overrideComponent(ExpandedViewDialogComponent, {
        set: {
          imports: [MatIconModule, MatTooltipModule, CompliancePanelStubComponent,
            RouteViewStubComponent, TimelineViewStubComponent]
        }
      })
      .compileComponents();

    fixture = TestBed.createComponent(ExpandedViewDialogComponent);
    fixture.detectChanges();
  };

  it('UL-T9: route renders the panel and the route view, wired like the tab', async () => {
    await setup('route');

    const panel = fixture.debugElement.query(By.directive(CompliancePanelStubComponent));
    const routeView = fixture.debugElement.query(By.directive(RouteViewStubComponent));
    expect(panel).not.toBeNull();
    expect(routeView).not.toBeNull();
    expect(panel.componentInstance.solution).toBe(solution);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Route');

    const compliance = { solution_id: 9, routes: [] };
    panel.componentInstance.complianceChange.emit(compliance);
    fixture.detectChanges();
    expect(routeView.componentInstance.compliance).toBe(compliance);

    routeView.componentInstance.complianceRecheckNeeded.emit();
    expect(panel.componentInstance.recheck).toHaveBeenCalled();
  });

  it('UL-T9: a focused violation closes the dialog', async () => {
    await setup('route');

    fixture.debugElement.query(By.directive(CompliancePanelStubComponent))
      .componentInstance.violationFocused.emit();

    expect(close).toHaveBeenCalled();
  });

  it('UL-T9: Collapse closes the dialog', async () => {
    await setup('timeline');

    expect(fixture.debugElement.query(By.directive(TimelineViewStubComponent))).not.toBeNull();
    expect(fixture.debugElement.query(By.directive(CompliancePanelStubComponent))).toBeNull();

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.btn-collapse')!.click();

    expect(close).toHaveBeenCalled();
  });
});
