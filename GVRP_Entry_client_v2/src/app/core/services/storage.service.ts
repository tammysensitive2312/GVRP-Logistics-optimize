// src/app/core/services/storage.service.ts
import { Injectable } from '@angular/core';

/**
 * Storage Service - Type-safe wrapper for localStorage/sessionStorage
 * Uses consolidated storage keys for better organization
 */

// Auth session data structure
export interface AuthSession {
  token: string;
  user: {
    id: number;
    username: string;
    role: string;
  };
  branchId: number;
  rememberMe: boolean;
  loginTime: number; // timestamp
}

// App state structure
export interface AppState {
  currentScreen?: string;
  selectedOrders?: number[];
  selectedVehicles?: number[];
  filters?: {
    date: string;
    status: string;
    priority: string;
    search: string;
  };
  activeSolutionId?: number;
  activeJobId?: number;
  sidebarCollapsed?: boolean;
  lastUrl?: string;
}

@Injectable({
  providedIn: 'root'
})
export class StorageService {

  // Consolidated storage keys
  private readonly KEYS = {
    AUTH_SESSION: 'vrp_auth_session',  // Consolidated auth data
    APP_STATE: 'vrp_app_state'         // Consolidated app state
  };

  constructor() { }

  // ============================================
  // Generic Storage Methods
  // ============================================

  /**
   * Set item in storage (localStorage or sessionStorage)
   */
  private setItem(key: string, value: any, persistent: boolean = false): void {
    try {
      const storage = persistent ? localStorage : sessionStorage;
      const serialized = JSON.stringify(value);
      storage.setItem(key, serialized);
    } catch (error) {
      console.error('Storage error:', error);
    }
  }

  /**
   * Get item from storage (tries sessionStorage first, then localStorage)
   */
  private getItem<T>(key: string): T | null {
    try {
      // Try sessionStorage first
      let item = sessionStorage.getItem(key);

      // Fallback to localStorage (for remember me)
      if (!item) {
        item = localStorage.getItem(key);
      }

      return item ? JSON.parse(item) : null;
    } catch (error) {
      console.error('Storage retrieval error:', error);
      return null;
    }
  }

  /**
   * Remove item from both storages
   */
  private removeItem(key: string): void {
    try {
      sessionStorage.removeItem(key);
      localStorage.removeItem(key);
    } catch (error) {
      console.error('Storage removal error:', error);
    }
  }

  /**
   * Clear all storage
   */
  clearAll(): void {
    try {
      sessionStorage.clear();
      localStorage.clear();
    } catch (error) {
      console.error('Storage clear error:', error);
    }
  }

  // ============================================
  // Authentication Session Management
  // ============================================

  /**
   * Save complete auth session (token + user + branch)
   */
  saveAuthSession(session: AuthSession): void {
    const storage = session.rememberMe ? localStorage : sessionStorage;

    // Add login timestamp
    const sessionWithTimestamp: AuthSession = {
      ...session,
      loginTime: Date.now()
    };

    storage.setItem(this.KEYS.AUTH_SESSION, JSON.stringify(sessionWithTimestamp));
  }

  /**
   * Get complete auth session
   */
  getAuthSession(): AuthSession | null {
    return this.getItem<AuthSession>(this.KEYS.AUTH_SESSION);
  }

  /**
   * Clear auth session
   */
  clearAuthSession(): void {
    this.removeItem(this.KEYS.AUTH_SESSION);
  }

  /**
   * Check if auth session exists and is valid
   */
  hasValidAuthSession(): boolean {
    const session = this.getAuthSession();
    if (!session) return false;

    // Optional: Check session age (e.g., max 7 days for remember me)
    const maxAge = session.rememberMe ? 7 * 24 * 60 * 60 * 1000 : 24 * 60 * 60 * 1000;
    const sessionAge = Date.now() - session.loginTime;

    if (sessionAge > maxAge) {
      this.clearAuthSession();
      return false;
    }

    return !!(session.token && session.user && session.branchId);
  }

  // ============================================
  // Convenience Methods for Auth Data
  // ============================================

  /**
   * Get authentication token
   */
  getToken(): string | null {
    const session = this.getAuthSession();
    return session?.token || null;
  }

  /**
   * Get current user
   */
  getUser(): AuthSession['user'] | null {
    const session = this.getAuthSession();
    return session?.user || null;
  }

  /**
   * Get branch ID
   */
  getBranch(): number | null {
    const session = this.getAuthSession();
    return session?.branchId || null;
  }

  /**
   * Check if remember me is enabled
   */
  isRememberMeEnabled(): boolean {
    const session = this.getAuthSession();
    return session?.rememberMe || false;
  }

  /**
   * Update specific auth session fields
   */
  updateAuthSession(updates: Partial<AuthSession>): void {
    const currentSession = this.getAuthSession();
    if (!currentSession) return;

    const updatedSession: AuthSession = {
      ...currentSession,
      ...updates
    };

    this.saveAuthSession(updatedSession);
  }

  // ============================================
  // Application State Management
  // ============================================

  /**
   * Save complete application state
   */
  saveAppState(state: AppState): void {
    try {
      localStorage.setItem(this.KEYS.APP_STATE, JSON.stringify(state));
    } catch (error) {
      console.error('Failed to save app state:', error);
    }
  }

  /**
   * Get complete application state
   */
  getAppState(): AppState | null {
    try {
      const state = localStorage.getItem(this.KEYS.APP_STATE);
      return state ? JSON.parse(state) : null;
    } catch (error) {
      console.error('Failed to load app state:', error);
      return null;
    }
  }

  /**
   * Update specific app state fields
   */
  updateAppState(updates: Partial<AppState>): void {
    const currentState = this.getAppState() || {};
    const updatedState: AppState = {
      ...currentState,
      ...updates
    };
    this.saveAppState(updatedState);
  }

  /**
   * Clear application state
   */
  clearAppState(): void {
    localStorage.removeItem(this.KEYS.APP_STATE);
  }

  // ============================================
  // Utility Methods
  // ============================================

  /**
   * Check if storage is available
   */
  isStorageAvailable(): boolean {
    try {
      const test = '__storage_test__';
      localStorage.setItem(test, test);
      localStorage.removeItem(test);
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Get storage usage info (debugging)
   */
  getStorageInfo(): { used: number; available: number } {
    try {
      let used = 0;
      for (let key in localStorage) {
        if (localStorage.hasOwnProperty(key)) {
          used += localStorage[key].length + key.length;
        }
      }

      // Approximate 5MB limit for localStorage
      const available = 5 * 1024 * 1024;

      return { used, available };
    } catch {
      return { used: 0, available: 0 };
    }
  }


}
