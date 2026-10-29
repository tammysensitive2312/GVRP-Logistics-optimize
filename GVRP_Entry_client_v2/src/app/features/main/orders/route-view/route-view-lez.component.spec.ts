import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';

import {
  ComplianceViolationDTO,
  EvaluatePreviewDTO,
  RouteDTO,
  SolutionComplianceDTO,
  SolutionDTO,
  StopDTO
} from '@core/models';
import { ApiService } from '@core/services/api.service';
import { SolutionStore } from '@core/services/solution.store';
import { ToastService } from '@shared/services/toast.service';

import { RouteViewComponent } from './route-view.component';

const stop = (partial: Partial<StopDTO>): StopDTO => ({
  type: 'ORDER',
  location_name: 'Order A',
  latitude: 21,
  longitude: 105,
  arrival_time: '08:30:00',
  load_after: 50,
  wait_time: 0,
  ...partial
});

const buildRoute = (id: number, plate: string): RouteDTO => ({
  id,
  vehicle_id: id,
  vehicle_license_plate: plate,
  start_time: '08:00:00',
  end_time: '10:00:00',
  distance: 20.44,
  service_time: 1.5,
  order_count: 1,
  load_utilization: 62.5,
  stops: [
    stop({ type: 'DEPOT', location_name: 'Central depot', departure_time: '08:00:00' }),
    stop({ location_name: 'Order A', demand: 50, departure_time: '08:45:00' }),
    stop({ type: 'DEPOT', location_name: 'Central depot' })
  ]
});

const solution = (): SolutionDTO => ({
  id: 9,
  job_id: 4,
  total_cost: 100,
  total_distance: 42.37,
  total_time: 2.5,
  total_co2: 12.3,
  total_vehicles_used: 1,
  served_orders: 1,
  unserved_orders: 0,
  routes: [buildRoute(11, '29A-11111')]
});

const compliance = (
  status: 'UNCHECKED' | 'COMPLIANT' | 'VIOLATION' | 'STALE'
): SolutionComplianceDTO => ({
  solution_id: 9,
  routes: [{
    route_id: 11,
    status,
    checked_at: '2027-01-01T08:00:00',
    violations: status === 'VIOLATION'
      ? [{ code: 'ZONE_FORBIDDEN', seq: 1, zone_key: 'Z17' }]
      : []
  }],
  zones: [{ key: 'Z17', branch_zone_id: 17, name: 'Hanoi LEZ' }],
  lez_unassigned: []
});

/** A VIOLATION whose stored violation list is exactly the one given. */
const violationCompliance = (
  violations: ComplianceViolationDTO[]
): SolutionComplianceDTO => ({
  solution_id: 9,
  routes: [{
    route_id: 11,
    status: 'VIOLATION',
    checked_at: '2027-01-01T08:00:00',
    violations
  }],
  zones: [{ key: 'Z17', branch_zone_id: 17, name: 'Hanoi LEZ' }],
  lez_unassigned: []
});

/** A plan the edit session can save: every order stop carries its order id. */
const editableSolution = (): SolutionDTO => {
  const dto = solution();
  dto.routes[0].stops[1].order_id = 77;
  return dto;
};

/** The shape `errorInterceptor` re-throws: message, status, originalError. */
const interceptorError = (status: number, message: string): unknown => ({
  status,
  message,
  originalError: { error: { errors: [{ message }] } }
});

/** RV-T1: the send rule of the route view (LEZ-001 P1c, design §3.6). */
describe('RouteViewComponent LEZ send rule', () => {
  let fixture: ComponentFixture<RouteViewComponent>;
  let component: RouteViewComponent;
  let api: jasmine.SpyObj<ApiService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let toast: jasmine.SpyObj<ToastService>;

  const setup = async (dto: SolutionComplianceDTO | null): Promise<void> => {
    api = jasmine.createSpyObj<ApiService>('ApiService', [
      'evaluateRoutes', 'saveRouteRevision', 'latestRevisionId', 'routeSends', 'sendRoute'
    ]);
    api.routeSends.and.returnValue(of([]));
    const store = jasmine.createSpyObj<SolutionStore>('SolutionStore', ['setSolution', 'loadById']);
    dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    dialog.open.and.returnValue({
      afterClosed: () => of({ driverUserId: 3, solutionId: 9, driverName: 'Anh Ba' })
    } as never);
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);

    await TestBed.configureTestingModule({
      imports: [RouteViewComponent],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: SolutionStore, useValue: store },
        { provide: MatDialog, useValue: dialog },
        { provide: ToastService, useValue: toast }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(RouteViewComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('solution', solution());
    if (dto) {
      fixture.componentRef.setInput('compliance', dto);
    }
    fixture.detectChanges();
  };

  const sendButton = (): HTMLButtonElement => {
    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button')
    ) as HTMLButtonElement[];
    return buttons.find(button => (button.textContent ?? '').trim() === 'Send to driver')!;
  };

  it('VIOLATION disables Send and names the violation code', async () => {
    await setup(compliance('VIOLATION'));

    expect(sendButton().disabled).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('ZONE_FORBIDDEN');
  });

  it('STALE disables Send and asks for a recheck', async () => {
    await setup(compliance('STALE'));

    expect(sendButton().disabled).toBeTrue();
    expect(fixture.nativeElement.textContent)
      .toContain('Recheck the low-emission compliance before sending.');
  });

  it('UNCHECKED disables Send and asks for a recheck', async () => {
    await setup(compliance('UNCHECKED'));

    expect(sendButton().disabled).toBeTrue();
    expect(fixture.nativeElement.textContent)
      .toContain('Recheck the low-emission compliance before sending.');
  });

  it('COMPLIANT leaves Send enabled', async () => {
    await setup(compliance('COMPLIANT'));

    expect(sendButton().disabled).toBeFalse();
    expect(component.sendBlockedReason(component.routeCards()[0])).toBeNull();
  });

  it('an unknown compliance keeps today\'s behaviour', async () => {
    await setup(null);

    expect(sendButton().disabled).toBeFalse();
    expect(component.sendBlockedReason(component.routeCards()[0])).toBeNull();
  });

  it('a 422 on send asks the panel for a fresh verdict', async () => {
    await setup(null);
    api.sendRoute.and.returnValue(throwError(() => ({
      status: 422,
      error: { errors: [{ message: 'Compliance is stale; recheck before sending' }] }
    })));
    const recheck = jasmine.createSpy('recheck');
    component.complianceRecheckNeeded.subscribe(recheck);

    component.openSendDialog(component.routeCards()[0]);

    expect(toast.error).toHaveBeenCalledWith('Compliance is stale; recheck before sending');
    expect(recheck).toHaveBeenCalled();
  });

  // ---------- review-p1c R1: geometry-only violations do not block ----------

  it('a geometry-only violation leaves Send enabled and explains the fetch', async () => {
    await setup(violationCompliance([
      { code: 'LEZ_GEOMETRY_MISSING', seq: 0 },
      { code: 'LEZ_GEOMETRY_MISSING', seq: 1 }
    ]));

    expect(component.sendBlockedReason(component.routeCards()[0])).toBeNull();
    expect(sendButton().disabled).toBeFalse();
    expect(fixture.nativeElement.textContent)
      .toContain('Road geometry is fetched and checked when you send.');
  });

  it('any other code next to a geometry violation still blocks the send', async () => {
    await setup(violationCompliance([
      { code: 'LEZ_GEOMETRY_MISSING', seq: 0 },
      { code: 'ZONE_ACCESS', seq: 1, zone_key: 'Z17' }
    ]));

    expect(sendButton().disabled).toBeTrue();
    expect(component.sendBlockedReason(component.routeCards()[0])).toContain('ZONE_ACCESS');
    expect(fixture.nativeElement.textContent)
      .not.toContain('Road geometry is fetched and checked when you send.');
  });

  // ---------- review-p1c R2: the server's refusal text ----------

  it('shows the server refusal that arrives behind the interceptor wrapper', async () => {
    await setup(null);
    api.sendRoute.and.returnValue(
      throwError(() => interceptorError(422, 'ZONE_ACCESS seq=1 zone=Z17')));
    const recheck = jasmine.createSpy('recheck');
    component.complianceRecheckNeeded.subscribe(recheck);

    component.openSendDialog(component.routeCards()[0]);

    expect(toast.error).toHaveBeenCalledWith('ZONE_ACCESS seq=1 zone=Z17');
    expect(recheck).toHaveBeenCalled();
  });

  const failingSave = async (error: unknown): Promise<void> => {
    await setup(null);
    fixture.componentRef.setInput('solution', editableSolution());
    fixture.detectChanges();
    api.evaluateRoutes.and.returnValue(of({
      feasible: true, violations: [], routes: [], totals: null, warnings: []
    }));
    api.saveRouteRevision.and.returnValue(throwError(() => error));

    component.startEdit();
    component.save();
  };

  it('shows the server text when the save is refused', async () => {
    await failingSave(interceptorError(422, 'ZONE_ACCESS seq=1 zone=Z17'));

    expect(component.saveError()).toBe('ZONE_ACCESS seq=1 zone=Z17');
  });

  it('keeps the conflict instruction after the server text', async () => {
    await failingSave(interceptorError(409, 'A newer revision exists'));

    expect(component.saveError())
      .toBe('A newer revision exists Load the latest revision to continue.');
    expect(component.saveConflict()).toBeTrue();
  });
});
