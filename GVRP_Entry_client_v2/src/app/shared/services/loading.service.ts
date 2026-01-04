// src/app/shared/services/loading.service.ts
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';

/**
 * Loading Service
 * - Manages global loading state
 * - Prevents flickering with debounce
 * - Tracks multiple concurrent loading operations
 */
@Injectable({
  providedIn: 'root'
})
export class LoadingService {

  private loadingSubject = new BehaviorSubject<boolean>(false);
  private loadingCounter = 0;
  private loadingMessage = new BehaviorSubject<string>('Loading...');

  // Public observable (debounced to prevent flickering)
  public loading$: Observable<boolean> = this.loadingSubject.asObservable().pipe(
    debounceTime(100), // Wait 100ms before showing/hiding
    distinctUntilChanged()
  );

  public loadingMessage$: Observable<string> = this.loadingMessage.asObservable();

  constructor() { }

  /**
   * Show loading indicator
   */
  show(message: string = 'Loading...'): void {
    this.loadingCounter++;
    this.loadingMessage.next(message);
    this.updateLoadingState();
  }

  /**
   * Hide loading indicator
   */
  hide(): void {
    if (this.loadingCounter > 0) {
      this.loadingCounter--;
    }
    this.updateLoadingState();
  }

  /**
   * Force hide loading (reset counter)
   */
  forceHide(): void {
    this.loadingCounter = 0;
    this.updateLoadingState();
  }

  /**
   * Check if loading is active
   */
  isLoading(): boolean {
    return this.loadingSubject.value;
  }

  /**
   * Update loading state based on counter
   */
  private updateLoadingState(): void {
    const isLoading = this.loadingCounter > 0;
    this.loadingSubject.next(isLoading);
  }
}
