import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

interface AdminMenuItem {
  path: string;
  icon: string;
  label: string;
}

/**
 * Admin shell
 *
 * Migrated from V1 `#screen-admin-settings` + `screens/admin-settings.js`.
 * V1 switched sections by toggling `.active` classes; here each section is a
 * child route, so admin pages are deep-linkable and lazily loaded.
 *
 * Lives outside MainLayout because V1's admin screen had its own navbar.
 */
@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './admin-layout.component.html',
  styleUrl: './admin-layout.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminLayoutComponent {
  private readonly router = inject(Router);

  readonly menuItems: readonly AdminMenuItem[] = [
    { path: 'vehicle-types', icon: '🚚', label: 'Vehicle Types' },
    { path: 'vehicles', icon: '🚗', label: 'Vehicles' },
    { path: 'depots', icon: '🏢', label: 'Depots' }
  ];

  /** Mobile drawer state (V1: `AdminSettings.toggleSidebar()`). */
  readonly sidebarOpen = signal(false);

  toggleSidebar(): void {
    this.sidebarOpen.update(open => !open);
  }

  closeSidebar(): void {
    this.sidebarOpen.set(false);
  }

  backToDashboard(): void {
    void this.router.navigate(['/main']);
  }
}
