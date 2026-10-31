import { TestBed } from '@angular/core/testing';
import { NavigationEnd, Router } from '@angular/router';
import { Subject } from 'rxjs';

import { LanguageService } from '@core/i18n/language.service';
import { SessionResetService } from '@core/services/session-reset.service';
import { StorageService } from '@core/services/storage.service';

import { translocoTesting } from '../../../../../testing/transloco-testing';
import { NavbarComponent } from './navbar.component';

/**
 * The navbar's logout used to clear only the auth/app-state keys, so the root
 * stores still held the branch's solution when the next user signed in.
 */
describe('NavbarComponent logout', () => {
  it('drops branch state together with the stored session', () => {
    const router = {
      events: new Subject<NavigationEnd>(),
      url: '/main',
      navigate: jasmine.createSpy('navigate')
    };
    const storage = jasmine.createSpyObj('StorageService', [
      'getUser',
      'getBranchName',
      'getBranch',
      'clearAuthSession',
      'clearAppState'
    ]);
    storage.getUser.and.returnValue(null);
    storage.getBranchName.and.returnValue('');
    storage.getBranch.and.returnValue(null);
    const sessionReset = jasmine.createSpyObj<SessionResetService>('SessionResetService', [
      'resetBranchState'
    ]);
    const language = {
      toggle: jasmine.createSpy('toggle'),
      current: () => 'en',
      currentOption: () => ({ code: 'en', label: 'English', flag: 'gb' }),
      nextOption: () => ({ code: 'vi', label: 'Tiếng Việt', flag: 'vn' })
    };

    TestBed.configureTestingModule({
      imports: [NavbarComponent, translocoTesting()],
      providers: [
        { provide: Router, useValue: router },
        { provide: LanguageService, useValue: language },
        { provide: StorageService, useValue: storage },
        { provide: SessionResetService, useValue: sessionReset }
      ]
    });

    const fixture = TestBed.createComponent(NavbarComponent);
    fixture.detectChanges();

    fixture.componentInstance.onLogout();

    expect(storage.clearAuthSession).toHaveBeenCalled();
    expect(storage.clearAppState).toHaveBeenCalled();
    expect(sessionReset.resetBranchState).toHaveBeenCalledTimes(1);
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });
});
