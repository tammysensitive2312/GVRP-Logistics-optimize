import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';

import { AuthService } from '@core/services/auth.service';
import { StorageService } from '@core/services/storage.service';

import { DriverGuard } from './driver.guard';

describe('DriverGuard', () => {
  let guard: DriverGuard;
  let auth: jasmine.SpyObj<AuthService>;
  let storage: jasmine.SpyObj<StorageService>;
  let router: jasmine.SpyObj<Router>;

  const state = { url: '/driver' } as never;

  beforeEach(() => {
    auth = jasmine.createSpyObj<AuthService>('AuthService',
      ['isAuthenticated', 'getRole']);
    storage = jasmine.createSpyObj<StorageService>('StorageService', ['updateAppState']);
    router = jasmine.createSpyObj<Router>('Router', ['createUrlTree']);
    router.createUrlTree.and.callFake(((segments: unknown[]) =>
      ({ segments } as unknown)) as never);

    TestBed.configureTestingModule({
      providers: [
        DriverGuard,
        { provide: AuthService, useValue: auth },
        { provide: StorageService, useValue: storage },
        { provide: Router, useValue: router }
      ]
    });
    guard = TestBed.inject(DriverGuard);
  });

  it('allows authenticated drivers', () => {
    auth.isAuthenticated.and.returnValue(true);
    auth.getRole.and.returnValue('DRIVER');

    expect(guard.canActivate({} as never, state)).toBeTrue();
  });

  it('sends authenticated non-drivers to /main', () => {
    auth.isAuthenticated.and.returnValue(true);
    auth.getRole.and.returnValue('PLANNER');

    guard.canActivate({} as never, state);

    expect(router.createUrlTree).toHaveBeenCalledWith(['/main']);
  });

  it('parks anonymous users on login with the return url', () => {
    auth.isAuthenticated.and.returnValue(false);

    guard.canActivate({} as never, state);

    expect(storage.updateAppState).toHaveBeenCalledWith({ lastUrl: '/driver' });
    expect(router.createUrlTree).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/driver' }
    });
  });
});
