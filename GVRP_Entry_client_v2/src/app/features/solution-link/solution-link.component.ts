import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { AuthService } from '@core/services/auth.service';
import { SolutionStore } from '@core/services/solution.store';
import { StorageService } from '@core/services/storage.service';
import { ToastService } from '@shared/services/toast.service';

/**
 * Landing component for the exact "" route (email deep links `?solution=<id>`).
 *
 * <p>Has no UI of its own: it resolves the link target in {@code ngOnInit} and
 * navigates away. Anonymous users are parked on /login with the full link kept
 * as the post-login redirect, so the solution loads without a second click.
 */
@Component({
  selector: 'app-solution-link',
  standalone: true,
  imports: [],
  template: ''
})
export class SolutionLinkComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly store = inject(SolutionStore);
  private readonly storage = inject(StorageService);
  private readonly toast = inject(ToastService);

  ngOnInit(): void {
    const solutionId = this.solutionId();
    if (solutionId === null) {
      void this.router.navigate([this.auth.isAuthenticated() ? '/main' : '/login']);
      return;
    }

    if (!this.auth.isAuthenticated()) {
      this.storage.updateAppState({ lastUrl: `/?solution=${solutionId}` });
      void this.router.navigate(['/login']);
      return;
    }

    this.store.loadById(solutionId).subscribe({
      next: () => {
        this.toast.success('Solution loaded! Switch between tabs to view it');
        void this.router.navigate(['/main']);
      },
      error: (error: unknown) => {
        this.toast.error(extractMessage(error) ?? 'Failed to load solution');
        void this.router.navigate(['/main']);
      }
    });
  }

  private solutionId(): number | null {
    const raw = this.route.snapshot.queryParamMap.get('solution');
    if (raw === null) return null;
    const parsed = Number(raw);
    return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
  }
}

function extractMessage(error: unknown): string | null {
  if (typeof error === 'object' && error !== null && 'message' in error) {
    const message = (error as { message?: unknown }).message;
    if (typeof message === 'string' && message.trim().length > 0) return message;
  }
  return null;
}
