import { Injectable } from '@angular/core';
import {
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpInterceptor,
  HttpErrorResponse
} from '@angular/common/http';
import {StorageService} from '@core/services/storage.service';
import {Router} from '@angular/router';
import {Observable, throwError} from 'rxjs';
import {catchError} from 'rxjs/operators';
import {environment} from '@environments/environment';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor(
    private storage: StorageService,
    private router: Router
  ) {
  }

  intercept(req: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const token = this.storage.getToken();

    let authRequest = req;

    if (token && this.shouldAddToken(req)) {
      authRequest = req.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      })
    }

    return next.handle(authRequest).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401) {
          this.handleUnauthorized();
        }
        return throwError(() => error)
      })
    );
  }

  private shouldAddToken(request: HttpRequest<any>): boolean {
    const isApiUrl = request.url.startsWith(environment.apiUrl);

    const isAuthEndpoint = request.url.includes('/auth/login') ||
      request.url.includes('/auth/register');

    return isApiUrl && !isAuthEndpoint;
  }


  private handleUnauthorized() {
    console.warn('Unauthorized - redirecting to login');

    this.storage.clearAuthSession();
    const currentUrl = this.router.url;
    if (currentUrl && !currentUrl.includes('login')) {
      this.storage.updateAppState({lastUrl: currentUrl});
    }

    this.router.navigate(['/login'])
  }
}

