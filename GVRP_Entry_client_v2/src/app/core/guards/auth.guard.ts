import {ActivatedRouteSnapshot, CanActivateChildFn, Router, RouterStateSnapshot, UrlTree} from '@angular/router';
import {Injectable} from '@angular/core';
import {AuthService} from '@core/services/auth.service';
import {StorageService} from '@core/services/storage.service';
import {Observable} from 'rxjs';
import {th} from 'date-fns/locale';

@Injectable({
  providedIn: 'root'
})
export class AuthGuard {

  constructor(
    private authService: AuthService,
    private storage: StorageService,
    private router: Router
  ) {}

  canActivate(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree {
    if (this.authService.isAuthenticated()) {
      return true;
    }

    this.storage.updateAppState({ lastUrl: state.url})
    console.warn('🔒 Access denied - redirecting to login');

    return this.router.createUrlTree(['/login'], {
      queryParams: { returnUrl: state.url }
    })
  }

  canActivateChild(
    childRoute: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree {
    return this.canActivate(childRoute, state);
  }

}
