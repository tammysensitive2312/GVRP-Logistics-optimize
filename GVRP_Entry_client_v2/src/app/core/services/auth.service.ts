// src/app/core/services/auth.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, throwError } from 'rxjs';
import { tap, catchError, map } from 'rxjs/operators';

import { environment } from '@environments/environment';
import { StorageService, AuthSession } from './storage.service';
import { LoginRequest, AuthResponse, User } from '../models';

/**
 * Authentication Service
 * Handles login, logout, token management, and auth state
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {

  // Observable streams
  private currentUserSubject = new BehaviorSubject<User | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  private isAuthenticatedSubject = new BehaviorSubject<boolean>(false);
  public isAuthenticated$ = this.isAuthenticatedSubject.asObservable();

  constructor(
    private http: HttpClient,
    private router: Router,
    private storage: StorageService
  ) {
    // Initialize from storage on app start
    this.initializeAuthState();
  }

  // ============================================
  // Public API
  // ============================================

  /**
   * Login with credentials
   */
  login(credentials: LoginRequest, rememberMe: boolean = false): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${environment.apiUrl}/auth/login`, credentials)
      .pipe(
        tap(response => this.handleLoginSuccess(response, rememberMe)),
        catchError(error => this.handleError(error))
      );
  }

  /**
   * Logout current user
   */
  logout(): Observable<void> {
    const token = this.storage.getToken();

    if (!token) {
      this.clearSession();
      return throwError(() => new Error('No active session'));
    }

    return this.http.post<void>(`${environment.apiUrl}/auth/logout`, {})
      .pipe(
        tap(() => this.clearSession()),
        catchError(error => {
          // Even if logout fails, clear local session
          this.clearSession();
          return throwError(() => error);
        })
      );
  }

  /**
   * Check if user is authenticated
   */
  isAuthenticated(): boolean {
    const token = this.storage.getToken();

    if (!token) {
      return false;
    }

    // Validate token expiration
    if (this.isTokenExpired(token)) {
      this.clearSession();
      return false;
    }

    return true;
  }

  /**
   * Get current user
   */
  getCurrentUser(): User | null {
    return this.currentUserSubject.value;
  }

  /**
   * Get current branch ID
   */
  getCurrentBranchId(): number | null {
    return this.storage.getBranch();
  }

  /**
   * Get auth token
   */
  getToken(): string | null {
    return this.storage.getToken();
  }

  // ============================================
  // Private Methods
  // ============================================

  /**
   * Initialize auth state from storage
   */
  private initializeAuthState(): void {
    const session = this.storage.getAuthSession();

    if (session && session.token && !this.isTokenExpired(session.token)) {
      this.currentUserSubject.next(session.user);
      this.isAuthenticatedSubject.next(true);
    } else {
      this.clearSession();
    }
  }

  /**
   * Handle successful login
   */
  private handleLoginSuccess(response: AuthResponse, rememberMe: boolean): void {
    // Create auth session object
    const session: AuthSession = {
      token: response.access_token,
      user: {
        id: response.user_id,
        username: response.username,
        role: response.role
      },
      branchId: response.branch_id,
      rememberMe,
      loginTime: Date.now()
    };

    // Save consolidated session
    this.storage.saveAuthSession(session);

    // Update observables
    this.currentUserSubject.next(session.user);
    this.isAuthenticatedSubject.next(true);

    console.log('✅ Login successful:', session.user);
  }

  /**
   * Clear session and redirect to login
   */
  private clearSession(): void {
    this.storage.clearAuthSession();
    this.currentUserSubject.next(null);
    this.isAuthenticatedSubject.next(false);

    // Save current URL for redirect after login
    const currentUrl = this.router.url;
    if (currentUrl && !currentUrl.includes('login')) {
      this.storage.updateAppState({ lastUrl: currentUrl });
    }

    this.router.navigate(['/login']);
  }

  /**
   * Check if JWT token is expired
   */
  private isTokenExpired(token: string): boolean {
    try {
      const payload = this.parseJwt(token);

      if (!payload.exp) {
        return false; // No expiration
      }

      const expirationTime = payload.exp * 1000; // Convert to milliseconds
      const currentTime = Date.now();

      return currentTime >= expirationTime;
    } catch (error) {
      console.error('Invalid token format:', error);
      return true; // Treat invalid tokens as expired
    }
  }

  /**
   * Parse JWT token payload
   */
  private parseJwt(token: string): any {
    try {
      const base64Url = token.split('.')[1];
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(
        atob(base64)
          .split('')
          .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );
      return JSON.parse(jsonPayload);
    } catch (error) {
      throw new Error('Invalid JWT token');
    }
  }

  /**
   * Handle HTTP errors
   */
  private handleError(error: any): Observable<never> {
    let errorMessage = 'An error occurred';

    if (error.error instanceof ErrorEvent) {
      // Client-side error
      errorMessage = error.error.message;
    } else if (error.error?.message) {
      // Server-side error with message
      errorMessage = error.error.message;
    } else if (error.status) {
      // HTTP error with status
      errorMessage = `Error ${error.status}: ${error.statusText}`;
    }

    console.error('Auth error:', errorMessage);
    return throwError(() => new Error(errorMessage));
  }

  /**
   * Get redirect URL after login
   */
  getRedirectUrl(): string {
    const state = this.storage.getAppState();
    const url = state?.lastUrl;

    // Clear last URL
    if (url) {
      this.storage.updateAppState({ lastUrl: undefined });
    }

    return url || '/main';
  }
}
