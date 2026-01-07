import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { StorageService } from '@core/services/storage.service';
import { environment } from '@environments/environment';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const storage = inject(StorageService);
  const router = inject(Router);

  const token = storage.getToken();

  const isApiUrl = req.url.startsWith(environment.apiUrl);
  const isAuthEndpoint = req.url.includes('/auth/login') || req.url.includes('/auth/register');

  const shouldAddToken = token && isApiUrl && !isAuthEndpoint;

  let authReq = req;

  if (shouldAddToken) {
    authReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        // Logic xử lý Unauthorized (handleUnauthorized cũ)
        console.warn('Unauthorized - redirecting to login');

        storage.clearAuthSession();
        const currentUrl = router.url;

        if (currentUrl && !currentUrl.includes('login')) {
          storage.updateAppState({ lastUrl: currentUrl });
        }

        router.navigate(['/login']);
      }

      return throwError(() => error);
    })
  );
};
