import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';

import { AuthResponse } from '@core/models';
import { AuthService } from '@core/services/auth.service';
import { ToastService } from '@shared/services/toast.service';
import { LoadingService } from '@shared/services/loading.service';

import { LoginComponent } from './login.component';

describe('LoginComponent driver landing', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let auth: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    auth = jasmine.createSpyObj<AuthService>('AuthService',
      ['login', 'getRedirectUrl', 'getRole', 'isAuthenticated']);
    auth.login.and.returnValue(of({} as unknown as AuthResponse));
    auth.getRedirectUrl.and.returnValue('/main');
    const toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);
    const loading = jasmine.createSpyObj<LoadingService>('LoadingService', ['show', 'hide']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate', 'navigateByUrl']);

    await TestBed.configureTestingModule({
      imports: [LoginComponent, NoopAnimationsModule],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: ToastService, useValue: toast },
        { provide: LoadingService, useValue: loading },
        { provide: Router, useValue: router }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    component.loginForm.setValue({
      branch_name: 'Branch Huge 1', username: 'taylai',
      password: 'secret', rememberMe: false
    });
  });

  it('sends drivers to /driver instead of /main', () => {
    auth.getRole.and.returnValue('DRIVER');

    component.onSubmit();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/driver');
  });

  it('keeps planner landing unchanged', () => {
    auth.getRole.and.returnValue('PLANNER');

    component.onSubmit();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/main');
  });

  it('honours a driver return url under /driver', () => {
    auth.getRole.and.returnValue('DRIVER');
    auth.getRedirectUrl.and.returnValue('/driver');

    component.onSubmit();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/driver');
  });
});
