import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { Observable, of } from 'rxjs';

import { AuthService } from '@core/services/auth.service';
import { SolutionDTO } from '@core/models';
import { SolutionStore } from '@core/services/solution.store';
import { StorageService } from '@core/services/storage.service';
import { ToastService } from '@shared/services/toast.service';

import { SolutionLinkComponent } from './solution-link.component';

describe('SolutionLinkComponent', () => {
  let router: jasmine.SpyObj<Router>;
  let auth: jasmine.SpyObj<AuthService>;
  let store: jasmine.SpyObj<SolutionStore>;
  let storage: jasmine.SpyObj<StorageService>;
  let toast: jasmine.SpyObj<ToastService>;

  async function setup(solutionParam: string | null, authenticated: boolean) {
    router = jasmine.createSpyObj<Router>('Router', ['navigate', 'navigateByUrl']);
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['isAuthenticated']);
    store = jasmine.createSpyObj<SolutionStore>('SolutionStore', ['loadById']);
    storage = jasmine.createSpyObj<StorageService>('StorageService', ['updateAppState']);
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);
    auth.isAuthenticated.and.returnValue(authenticated);

    await TestBed.configureTestingModule({
      imports: [SolutionLinkComponent],
      providers: [
        { provide: Router, useValue: router },
        { provide: AuthService, useValue: auth },
        { provide: SolutionStore, useValue: store },
        { provide: StorageService, useValue: storage },
        { provide: ToastService, useValue: toast },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap(
                solutionParam === null ? {} : { solution: solutionParam })
            }
          }
        }
      ]
    }).compileComponents();
  }

  it('goes to /main when authenticated without a link', async () => {
    await setup(null, true);
    const fixture: ComponentFixture<SolutionLinkComponent> =
      TestBed.createComponent(SolutionLinkComponent);
    fixture.detectChanges();

    expect(router.navigate).toHaveBeenCalledWith(['/main']);
    expect(store.loadById).not.toHaveBeenCalled();
  });

  it('goes to /login when anonymous without a link', async () => {
    await setup(null, false);
    const fixture: ComponentFixture<SolutionLinkComponent> =
      TestBed.createComponent(SolutionLinkComponent);
    fixture.detectChanges();

    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('loads the solution and goes to /main when authenticated', async () => {
    await setup('47', true);
    store.loadById.and.returnValue(of({ id: 47 } as unknown as SolutionDTO));
    const fixture: ComponentFixture<SolutionLinkComponent> =
      TestBed.createComponent(SolutionLinkComponent);
    fixture.detectChanges();

    expect(store.loadById).toHaveBeenCalledWith(47);
    expect(toast.success).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/main']);
  });

  it('shows an error and goes to /main when loading fails', async () => {
    await setup('47', true);
    store.loadById.and.returnValue(
      new Observable<never>(subscriber => subscriber.error(new Error('gone'))));
    const fixture: ComponentFixture<SolutionLinkComponent> =
      TestBed.createComponent(SolutionLinkComponent);
    fixture.detectChanges();

    expect(toast.error).toHaveBeenCalledWith('gone');
    expect(router.navigate).toHaveBeenCalledWith(['/main']);
  });

  it('treats a non-numeric id as absent', async () => {
    await setup('abc', true);
    const fixture: ComponentFixture<SolutionLinkComponent> =
      TestBed.createComponent(SolutionLinkComponent);
    fixture.detectChanges();

    expect(store.loadById).not.toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/main']);
  });

  it('parks the link across login when anonymous', async () => {
    await setup('47', false);
    const fixture: ComponentFixture<SolutionLinkComponent> =
      TestBed.createComponent(SolutionLinkComponent);
    fixture.detectChanges();

    expect(storage.updateAppState).toHaveBeenCalledWith({ lastUrl: '/?solution=47' });
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
    expect(store.loadById).not.toHaveBeenCalled();
  });
});
