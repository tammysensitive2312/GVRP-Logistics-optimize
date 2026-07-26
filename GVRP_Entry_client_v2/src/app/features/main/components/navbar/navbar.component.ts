import {Component, EventEmitter, inject, Output} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {NavigationEnd, Router} from '@angular/router';
import {filter, map, startWith} from 'rxjs/operators';
import {MatToolbarModule} from '@angular/material/toolbar';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {User} from '@core/models';
import {MatDividerModule} from '@angular/material/divider';
import {MatTooltipModule} from '@angular/material/tooltip';
import {CommonModule} from '@angular/common';
import {MatButtonModule} from '@angular/material/button';
import {StorageService} from '@core/services/storage.service';
import {TranslocoPipe} from '@jsverse/transloco';
import {LanguageService} from '@core/i18n/language.service';
import {FlagIconComponent} from '@shared/components/flag-icon/flag-icon.component';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [
    CommonModule,
    MatToolbarModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatDividerModule,
    MatTooltipModule,
    TranslocoPipe,
    FlagIconComponent
  ],
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.scss'
})
export class NavbarComponent {
  private readonly router = inject(Router);
  private readonly storageService = inject(StorageService);

  readonly language = inject(LanguageService);

  /**
   * Derived from the URL instead of a field set on click.
   *
   * The old version set `activeTab` locally and then navigated; because /main and
   * /jobs are separate routes that each instantiate MainLayout, the navbar was
   * destroyed and rebuilt with `activeTab` back to its default. The route sniffing
   * that was supposed to fix that looked for 'history' in the URL, which is
   * '/jobs' - so the first click never highlighted the tab.
   */
  readonly activeTab = toSignal(
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd),
      map(() => tabFromUrl(this.router.url)),
      startWith(tabFromUrl(this.router.url))
    ),
    { initialValue: tabFromUrl(this.router.url) }
  );

  currentUser: User | null = null;
  branchName: string = '';

  @Output() tabChange = new EventEmitter<string>();


  ngOnInit(): void {
    this.loadCurrentUser();
    this.loadBranchName();
  }

  onTabClick(tab: string): void {
    this.tabChange.emit(tab);

    switch (tab) {
      case 'dashboard':
        this.router.navigate(['/main']).then();
        break;
      case 'analysis':
        this.showComingSoon('Analysis');
        break;
      case 'history':
        this.router.navigate(['/jobs']).then();
        break;
    }
  }

  onLanguageToggle(): void {
    this.language.toggle();
  }

  onProfileClick(): void {
    this.showComingSoon('Profile');
  }

  onAdminClick(): void {
    this.router.navigate(['/admin']).then();
  }

  onLogout(): void {
    this.clearUserSession();
    this.router.navigate(['/login']).then();
  }

  private loadBranchName(): void {
    this.branchName = this.storageService.getBranchName();

    if (!this.branchName) {
      const branchId = this.storageService.getBranch();
      if (branchId) this.branchName = `Branch #${branchId}`;
    }
  }

  private loadCurrentUser(): void {
    try {
      const user = this.storageService.getUser();

      if (user) {
        this.currentUser = user;
      } else {
        this.currentUser = null;
      }
    } catch (error) {
      console.error('Error loading user:', error);
      this.currentUser = null;
    }
  }

  private clearUserSession(): void {
    this.storageService.clearAuthSession();
    this.storageService.clearAppState();
    this.currentUser = null;
  }

  private showComingSoon(feature: string): void {
    alert(`${feature} - Coming soon!`);
  }
}

function tabFromUrl(url: string): string {
  if (url.startsWith('/jobs')) return 'history';
  if (url.startsWith('/analysis')) return 'analysis';
  if (url.startsWith('/main')) return 'dashboard';
  return '';
}
