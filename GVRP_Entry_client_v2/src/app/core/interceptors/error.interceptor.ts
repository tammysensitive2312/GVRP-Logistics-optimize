import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { throwError, timer } from 'rxjs';
import { catchError, retry } from 'rxjs/operators';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    // 1. Retry logic
    retry({
      count: 1,
      resetOnSuccess: true,
      delay: (error: HttpErrorResponse) => {
        const shouldNotRetry = !(error.status >= 500 && req.method === 'GET');
        if (shouldNotRetry) {
          return throwError(() => error);
        }
        return timer(1000);
      }
    }),

    // 2. Error Handling
    catchError((error: HttpErrorResponse) => {
      const errorMessage = getErrorMessage(error);

      // Log error
      console.error('HTTP Error:', {
        url: req.url,
        method: req.method,
        status: error.status,
        message: errorMessage,
        error
      });

      // Return user-friendly error structure
      return throwError(() => ({
        message: errorMessage,
        status: error.status,
        originalError: error
      }));
    })
  );
};


function getErrorMessage(error: HttpErrorResponse): string {
  // Client-side error
  if (error.error instanceof ErrorEvent) {
    return `Network error: ${error.error.message}`;
  }

  // Server-side error
  if (error.error?.message) {
    return error.error.message;
  }

  // HTTP status code errors
  switch (error.status) {
    case 0:
      return 'Unable to connect to server. Please check your internet connection.';
    case 400:
      return 'Invalid request. Please check your input.';
    case 401:
      return 'Unauthorized. Please login again.';
    case 403:
      return 'You do not have permission to perform this action.';
    case 404:
      return 'Resource not found.';
    case 409:
      return 'Conflict with existing data.';
    case 422:
      return 'Validation error. Please check your input.';
    case 500:
      return 'Internal server error. Please try again later.';
    case 502:
    case 503:
      return 'Service temporarily unavailable. Please try again later.';
    case 504:
      return 'Request timeout. Please try again.';
    default:
      return `An error occurred: ${error.statusText || 'Unknown error'}`;
  }
}
