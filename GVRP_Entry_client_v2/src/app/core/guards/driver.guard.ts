import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { Observable } from 'rxjs';

import { AuthService } from '@core/services/auth.service';
import { StorageService } from '@core/services/storage.service';

/**
 * Driver guard: only authenticated DRIVER users enter driver screens.
 * Server endpoints remain authoritative (403 on foreign data).
 */
@Injectable({
  providedIn: 'root'
})
export class DriverGuard {

  constructor(
    private authService: AuthService,
    private storage: StorageService,
    private router: Router
  ) {}

  canActivate(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree {
    if (this.authService.isAuthenticated() && this.authService.getRole() === 'DRIVER') {
      return true;
    }

    if (this.authService.isAuthenticated()) {
      return this.router.createUrlTree(['/main']);
    }

    this.storage.updateAppState({ lastUrl: state.url });
    return this.router.createUrlTree(['/login'], {
      queryParams: { returnUrl: state.url }
    });
  }
}
