// src/app/shared/services/toast.service.ts
import { Injectable } from '@angular/core';
import { MatSnackBar, MatSnackBarConfig } from '@angular/material/snack-bar';

/**
 * Toast Service
 * - Shows toast notifications using Material Snackbar
 * - Provides success, error, warning, and info variants
 */
@Injectable({
  providedIn: 'root'
})
export class ToastService {

  private defaultConfig: MatSnackBarConfig = {
    duration: 3000,
    horizontalPosition: 'right',
    verticalPosition: 'top',
  };

  constructor(private snackBar: MatSnackBar) { }

  /**
   * Show success toast
   */
  success(message: string, duration: number = 3000): void {
    this.show(message, 'success', duration);
  }

  /**
   * Show error toast
   */
  error(message: string, duration: number = 5000): void {
    this.show(message, 'error', duration);
  }

  /**
   * Show warning toast
   */
  warning(message: string, duration: number = 4000): void {
    this.show(message, 'warning', duration);
  }

  /**
   * Show info toast
   */
  info(message: string, duration: number = 3000): void {
    this.show(message, 'info', duration);
  }

  /**
   * Show generic toast
   */
  show(message: string, type: 'success' | 'error' | 'warning' | 'info' = 'info', duration: number = 3000): void {
    const config: MatSnackBarConfig = {
      ...this.defaultConfig,
      duration,
      panelClass: [`toast-${type}`]
    };

    this.snackBar.open(message, undefined, config);
  }

  /**
   * Show toast with action button
   */
  showWithAction(
    message: string,
    actionText: string,
    onAction: () => void,
    type: 'success' | 'error' | 'warning' | 'info' = 'info'
  ): void {
    const config: MatSnackBarConfig = {
      ...this.defaultConfig,
      duration: 5000,
      panelClass: [`toast-${type}`]
    };

    const snackBarRef = this.snackBar.open(message, actionText, config);

    snackBarRef.onAction().subscribe(() => {
      onAction();
    });
  }

  /**
   * Dismiss all toasts
   */
  dismiss(): void {
    this.snackBar.dismiss();
  }
}
