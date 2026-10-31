import { TestBed, fakeAsync, tick, discardPeriodicTasks } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AuthResponse, DepotDTO, SolutionDTO, VehicleTypeDTO } from '@core/models';
import { authInterceptor } from '@core/interceptors/auth.interceptor';
import { VehicleSelectionService } from '@shared/services/vehicle-selection.service';

import { AuthService } from './auth.service';
import { DepotStore } from './depot.store';
import { JobPollingService } from './job-polling.service';
import { SessionResetService } from './session-reset.service';
import { SolutionStore } from './solution.store';
import { StateService } from './state.service';
import { StorageService } from './storage.service';
import { VehicleTypeStore } from './vehicle-type.store';

/**
 * Cross-branch exposure in the client: a planner of branch A loaded solution 92,
 * then a planner of branch B signed in in the same tab (no reload) and still saw
 * solution 92, because the root stores outlived the session.
 */
describe('Session reset across branches', () => {
  let http: HttpTestingController;
  let router: { url: string; navigate: jasmine.Spy };

  let auth: AuthService;
  let solutions: SolutionStore;
  let depots: DepotStore;
  let vehicleTypes: VehicleTypeStore;
  let state: StateService;
  let vehicleSelection: VehicleSelectionService;
  let polling: JobPollingService;
  let storage: StorageService;

  const BRANCH_A: AuthResponse = {
    access_token: fakeJwt(),
    user_id: 11,
    username: 'planner-a',
    role: 'PLANNER',
    branch_id: 1,
    branch_name: 'Branch A'
  };
  const BRANCH_B: AuthResponse = {
    access_token: fakeJwt(),
    user_id: 22,
    username: 'planner-b',
    role: 'PLANNER',
    branch_id: 2,
    branch_name: 'Branch B'
  };

  const solution92 = { id: 92 } as SolutionDTO;
  const depotOfA = { id: 5, name: 'Depot A' } as DepotDTO;
  const vehicleTypeOfA = { id: 7, type_name: 'Van A' } as unknown as VehicleTypeDTO;

  const clearStorages = () => {
    sessionStorage.clear();
    localStorage.clear();
  };

  const login = (response: AuthResponse) => {
    auth.login({ username: response.username, password: 'x' } as never).subscribe();
    http.expectOne(req => req.url.endsWith('/auth/login')).flush(response);
  };

  const logout = () => {
    auth.logout().subscribe({ error: () => undefined });
    http.expectOne(req => req.url.endsWith('/auth/logout')).flush(null);
  };

  /** Everything branch A's planner had on screen before leaving. */
  const loadBranchAData = () => {
    solutions.loadById(92).subscribe();
    http.expectOne(req => req.url.endsWith('/solutions/92')).flush(solution92);

    depots.load().subscribe();
    http.expectOne(req => req.url.endsWith('/depots')).flush([depotOfA]);

    vehicleTypes.load().subscribe();
    http.expectOne(req => req.url.endsWith('/vehicle-types')).flush([vehicleTypeOfA]);

    state.selectOrder(301);
    vehicleSelection.setSelection([7]);

    expect(solutions.activeSolutionId()).toBe(92);
    expect(depots.depots().length).toBe(1);
    expect(vehicleTypes.vehicleTypes().length).toBe(1);
  };

  const expectNothingFromBranchA = async () => {
    expect(solutions.solution()).toBeNull();
    expect(solutions.activeSolutionId()).toBeNull();
    expect(depots.depots()).toEqual([]);
    expect(vehicleTypes.vehicleTypes()).toEqual([]);
    expect((await firstValueFrom(state.selectedOrders$)).size).toBe(0);
    expect(vehicleSelection.getSelectedIds()).toEqual([]);
    expect(polling.isPolling()).toBeFalse();
  };

  beforeEach(() => {
    clearStorages();
    router = { url: '/main', navigate: jasmine.createSpy('navigate') };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: router }
      ]
    });

    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    solutions = TestBed.inject(SolutionStore);
    depots = TestBed.inject(DepotStore);
    vehicleTypes = TestBed.inject(VehicleTypeStore);
    state = TestBed.inject(StateService);
    vehicleSelection = TestBed.inject(VehicleSelectionService);
    polling = TestBed.inject(JobPollingService);
    storage = TestBed.inject(StorageService);
  });

  afterEach(() => {
    http.verify();
    clearStorages();
  });

  it('logout, then login as another branch: solution 92 and A\'s lists are gone', async () => {
    login(BRANCH_A);
    loadBranchAData();

    router.url = '/jobs/92';
    logout();
    await expectNothingFromBranchA();

    login(BRANCH_B);
    await expectNothingFromBranchA();
    expect(auth.getCurrentBranchId()).toBe(2);
  });

  it('login as another branch WITHOUT logging out first still drops A\'s state', async () => {
    login(BRANCH_A);
    loadBranchAData();

    login(BRANCH_B);

    await expectNothingFromBranchA();
    expect(auth.getCurrentBranchId()).toBe(2);
  });

  it('explicit logout keeps no lastUrl, so B is not sent to A\'s /jobs/92', () => {
    login(BRANCH_A);
    router.url = '/jobs/92';

    logout();
    login(BRANCH_B);

    expect(storage.getAppState()?.lastUrl).toBeUndefined();
    expect(auth.getRedirectUrl()).toBe('/main');
  });

  it('a 401 from the API also drops branch state', async () => {
    login(BRANCH_A);
    loadBranchAData();

    depots.load().subscribe({ error: () => undefined });
    http
      .expectOne(req => req.url.endsWith('/depots'))
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    await expectNothingFromBranchA();
    expect(storage.getAuthSession()).toBeNull();
  });

  it('stops job polling that was started for the previous branch', fakeAsync(() => {
    login(BRANCH_A);
    polling.startPolling(40, 1000);
    expect(polling.isPolling()).toBeTrue();

    logout();

    expect(polling.isPolling()).toBeFalse();
    tick(5000);
    http.expectNone(req => req.url.includes('/jobs/40/progress'));
    discardPeriodicTasks();
  }));

  it('a solution request still in flight at logout does not land in B\'s session', () => {
    login(BRANCH_A);
    solutions.loadById(92).subscribe();
    const pending = http.expectOne(req => req.url.endsWith('/solutions/92'));

    logout();
    login(BRANCH_B);
    pending.flush(solution92);

    expect(solutions.solution()).toBeNull();
    expect(solutions.loading()).toBeFalse();
  });

  it('a depot request still in flight at logout does not land in B\'s session', () => {
    login(BRANCH_A);
    depots.load().subscribe();
    const pending = http.expectOne(req => req.url.endsWith('/depots'));

    logout();
    login(BRANCH_B);
    pending.flush([depotOfA]);

    expect(depots.depots()).toEqual([]);
    expect(depots.loading()).toBeFalse();
  });

  it('resetBranchState clears every store on its own', async () => {
    login(BRANCH_A);
    loadBranchAData();

    TestBed.inject(SessionResetService).resetBranchState();

    await expectNothingFromBranchA();
  });
});

/** Unsigned JWT with an `exp` one hour ahead; only the payload is read client-side. */
function fakeJwt(): string {
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  const exp = Math.floor(Date.now() / 1000) + 3600;
  return `${encode({ alg: 'none' })}.${encode({ exp })}.sig`;
}
