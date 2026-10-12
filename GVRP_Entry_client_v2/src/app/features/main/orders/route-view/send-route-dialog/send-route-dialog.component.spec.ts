import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';

import { ApiService } from '@core/services/api.service';

import { SendRouteDialogComponent } from './send-route-dialog.component';

describe('SendRouteDialogComponent', () => {
  let fixture: ComponentFixture<SendRouteDialogComponent>;
  let component: SendRouteDialogComponent;
  let api: jasmine.SpyObj<ApiService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<SendRouteDialogComponent>>;

  const drivers = [{ id: 2, username: 'taylai', full_name: 'Tai Lai', phone: '0901' }];
  const revisions = [{ id: 9, created_at: '2026-10-06T08:00:00', type: 'ENGINE_GENERATED', status: 'SUCCESS' }];

  beforeEach(async () => {
    api = jasmine.createSpyObj<ApiService>('ApiService',
      ['listDrivers', 'revisionChain', 'routeSends']);
    api.listDrivers.and.returnValue(of(drivers));
    api.revisionChain.and.returnValue(of(revisions));
    api.routeSends.and.returnValue(of([]));
    dialogRef = jasmine.createSpyObj<MatDialogRef<SendRouteDialogComponent>>(
      'MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      imports: [SendRouteDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: MatDialogRef, useValue: dialogRef },
        {
          provide: MAT_DIALOG_DATA,
          useValue: { routeId: 21, solutionId: 9, licensePlate: '29A-1' }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SendRouteDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads drivers, revisions and history on init', () => {
    expect(api.listDrivers).toHaveBeenCalled();
    expect(api.revisionChain).toHaveBeenCalledWith(9);
    expect(api.routeSends).toHaveBeenCalledWith(21);
    expect(component.drivers.length).toBe(1);
    expect(component.selectedSolutionId).toBe(9);
  });

  it('shows the current assignee when one exists', () => {
    api.routeSends.and.returnValue(of([{
      id: 1, solution_id: 9, route_id: 21, vehicle_license_plate: '29A-1',
      driver_id: 2, driver_name: 'Tai Lai', sender_name: 'boss',
      sent_at: '2026-10-06T09:00:00', status: 'CURRENT'
    }]));
    component.ngOnInit();
    fixture.detectChanges();

    expect(component.currentAssignee).toBe('Tai Lai');
    expect(fixture.nativeElement.textContent).toContain('supersedes it');
  });

  it('blocks submit without a driver and closes with the selection', () => {
    expect(component.canSubmit()).toBeFalse();

    component.selectedDriverId = 2;
    expect(component.canSubmit()).toBeTrue();
    component.onSubmit();

    expect(dialogRef.close).toHaveBeenCalledWith({ driverUserId: 2, solutionId: 9 });
  });

  it('cancels without a result and reports load failure', () => {
    api.listDrivers.and.returnValue(throwError(() => new Error('down')));
    component.ngOnInit();
    fixture.detectChanges();

    expect(component.loadError).toContain('Could not load drivers');
    expect(component.canSubmit()).toBeFalse();
    component.onCancel();
    expect(dialogRef.close).toHaveBeenCalledWith(null);
  });
});
