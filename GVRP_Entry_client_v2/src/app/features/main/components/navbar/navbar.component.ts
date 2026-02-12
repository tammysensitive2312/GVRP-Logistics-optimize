import {Component, EventEmitter, Output} from '@angular/core';
import {MatToolbar, MatToolbarModule} from '@angular/material/toolbar';
import {MatIcon, MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {User} from '@core/models';
import {Router} from '@angular/router';
import {MatDividerModule} from '@angular/material/divider';
import {CommonModule} from '@angular/common';
import {MatButtonModule} from '@angular/material/button';
import {StorageService} from '@core/services/storage.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [
    CommonModule,
    MatToolbarModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatDividerModule
  ],
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.scss'
})
export class NavbarComponent {
  activeTab: string = 'dashboard';
  currentUser: User | null = null;
  branchName: string = '';

  @Output() menuClick = new EventEmitter<void>();
  @Output() tabChange = new EventEmitter<string>();

  constructor(
    private router: Router,
    private storageService: StorageService
  ) {}

  ngOnInit(): void {
    this.loadCurrentUser();
    this.setActiveTabFromRoute();
  }

  onMenuClick(): void {
    this.menuClick.emit();
  }

  onTabClick(tab: string): void {
    this.activeTab = tab;
    this.tabChange.emit(tab);

    // Navigate to the corresponding route
    switch (tab) {
      case 'dashboard':
        // this.router.navigate(['/main']);
        break;
      case 'analysis':
        this.showComingSoon('Analysis');
        break;
      case 'history':
        this.showComingSoon('History');
        break;
    }
  }

  onProfileClick(): void {
    this.showComingSoon('Profile');
  }

  onAdminClick(): void {
    this.showComingSoon('Administrator');
  }

  onLogout(): void {
    this.clearUserSession();
    this.router.navigate(['/login']);
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

  private setActiveTabFromRoute(): void {
    const currentUrl = this.router.url;

    if (currentUrl.includes('dashboard') || currentUrl === '/main') {
      this.activeTab = 'dashboard';
    } else if (currentUrl.includes('analysis')) {
      this.activeTab = 'analysis';
    } else if (currentUrl.includes('history')) {
      this.activeTab = 'history';
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
