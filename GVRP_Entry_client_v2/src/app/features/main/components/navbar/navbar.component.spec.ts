import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NavigationEnd, Router } from '@angular/router';
import { Subject } from 'rxjs';

import { LanguageService } from '@core/i18n/language.service';
import { StorageService } from '@core/services/storage.service';

import { translocoTesting } from '../../../../../testing/transloco-testing';
import { NavbarComponent } from './navbar.component';

describe('NavbarComponent', () => {
  let fixture: ComponentFixture<NavbarComponent>;
  let component: NavbarComponent;

  let events$: Subject<NavigationEnd>;
  let router: { events: Subject<NavigationEnd>; url: string; navigate: jasmine.Spy };
  let language: jasmine.SpyObj<Pick<LanguageService, 'toggle'>>;
  let storage: jasmine.SpyObj<
    Pick<StorageService, 'getUser' | 'getBranchName' | 'getBranch' | 'clearAuthSession' | 'clearAppState'>
  >;

  const build = (url: string) => {
    router.url = url;

    fixture = TestBed.createComponent(NavbarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  beforeEach(async () => {
    events$ = new Subject<NavigationEnd>();
    router = { events: events$, url: '/main', navigate: jasmine.createSpy('navigate') };

    language = jasmine.createSpyObj<Pick<LanguageService, 'toggle'>>('LanguageService', [
      'toggle'
    ]);
    Object.defineProperty(language, 'current', { value: () => 'en', writable: true });
    Object.defineProperty(language, 'currentOption', {
      value: () => ({ code: 'en', label: 'English', flag: 'gb' }),
      writable: true
    });
    Object.defineProperty(language, 'nextOption', {
      value: () => ({ code: 'vi', label: 'Tiếng Việt', flag: 'vn' }),
      writable: true
    });

    storage = jasmine.createSpyObj('StorageService', [
      'getUser',
      'getBranchName',
      'getBranch',
      'clearAuthSession',
      'clearAppState'
    ]);
    storage.getUser.and.returnValue(null);
    storage.getBranchName.and.returnValue('');
    storage.getBranch.and.returnValue(null);

    await TestBed.configureTestingModule({
      imports: [NavbarComponent, translocoTesting()],
      providers: [
        { provide: Router, useValue: router },
        { provide: LanguageService, useValue: language },
        { provide: StorageService, useValue: storage }
      ]
    }).compileComponents();
  });

  it('derives the active tab from the current URL on creation', () => {
    build('/main');
    expect(component.activeTab()).toBe('dashboard');
  });

  it('highlights History on /jobs immediately - the reported bug', () => {
    build('/jobs');
    expect(component.activeTab()).toBe('history');
  });

  it('also matches the deep-linked /jobs/:id URL', () => {
    build('/jobs/42');
    expect(component.activeTab()).toBe('history');
  });

  it('follows navigation without needing a second click', () => {
    build('/main');
    expect(component.activeTab()).toBe('dashboard');

    router.url = '/jobs';
    events$.next(new NavigationEnd(1, '/main', '/jobs'));

    expect(component.activeTab()).toBe('history');
  });

  it('claims no tab on unrelated routes', () => {
    build('/admin/vehicle-types');
    expect(component.activeTab()).toBe('');
  });

  it('navigates when a tab is clicked', () => {
    build('/main');

    component.onTabClick('history');
    expect(router.navigate).toHaveBeenCalledWith(['/jobs']);

    component.onTabClick('dashboard');
    expect(router.navigate).toHaveBeenCalledWith(['/main']);
  });

  it('flips the language with a single click', () => {
    build('/main');

    component.onLanguageToggle();

    expect(language.toggle).toHaveBeenCalledTimes(1);
  });
});
