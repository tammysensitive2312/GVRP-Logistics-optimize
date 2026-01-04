// src/app/core/interceptors/loading.interceptor.ts
import { Injectable } from '@angular/core';
import {
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpInterceptor,
  HttpResponse
} from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap, finalize } from 'rxjs/operators';

import { LoadingService } from '@shared/services/loading.service';
import { SKIP_LOADING } from './loading.context';

/**
 * Loading Interceptor
 * - Shows/hides loading indicator for HTTP requests
 * - Tracks multiple concurrent requests
 */
@Injectable()
export class LoadingInterceptor implements HttpInterceptor {

  private activeRequests = 0;

  constructor(private loadingService: LoadingService) {}

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {

    if (this.shouldSkipLoading(request)) {
      return next.handle(request);
    }

    // Increment active requests and show loading
    this.activeRequests++;
    this.updateLoadingState();

    return next.handle(request).pipe(
      tap(event => {
        // Optional: handle response events
        if (event instanceof HttpResponse) {
          // Request completed successfully
        }
      }),
      finalize(() => {
        // Decrement active requests and update loading state
        this.activeRequests--;
        this.updateLoadingState();
      })
    );
  }

  /**
   * Update loading state based on active requests
   */
  private updateLoadingState(): void {
    if (this.activeRequests > 0) {
      this.loadingService.show();
    } else {
      this.loadingService.hide();
    }
  }

  /**
   * Check if loading should be skipped for this request
   */
  private shouldSkipLoading(request: HttpRequest<any>): boolean {
    if (request.context.get(SKIP_LOADING)) {
      return true;
    }

    return false;
  }
}
