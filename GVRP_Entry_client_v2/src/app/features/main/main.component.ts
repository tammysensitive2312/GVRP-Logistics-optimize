import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { MapComponent } from '@shared/components/map/map.component';

import { MatSidenavModule } from '@angular/material/sidenav';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';

import { DepotDTO, OrderDTO, SolutionDTO } from '@core/models';

import { AuthService } from '@core/services/auth.service';
import { StorageService } from '@core/services/storage.service';
import { MatMenuModule, MatMenuTrigger } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';

@Component({
  selector: 'app-main',
  standalone: true,
  imports: [
    CommonModule,
    MapComponent,
    MatSidenavModule,
    MatButtonModule,
    MatIconModule,
    MatToolbarModule,
    MatMenuModule,
    MatDividerModule,
    MatMenuTrigger
  ],
  templateUrl: './main.component.html',
  styleUrls: ['./main.component.scss']
})
export class MainComponent implements OnInit, OnDestroy {
  // State
  depots: DepotDTO[] = [];
  orders: OrderDTO[] = [];
  solution: SolutionDTO | null = null;
  sidebarOpened = true;

  // User info
  currentUser: any;
  currentBranch: any;

  private destroy$ = new Subject<void>();

  constructor(
    private authService: AuthService,
    private storage: StorageService
  ) {}

  ngOnInit(): void {
    this.loadUserInfo();
    this.loadInitialData();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadUserInfo(): void {
    const session = this.storage.getAuthSession();
    if (session) {
      this.currentUser = session.user;
      this.currentBranch = session.branchId;
    }
  }

  private loadInitialData(): void {
    // TODO: Load depots, vehicles, orders from API
    console.log('Loading initial data...');
  }

  toggleSidebar(): void {
    this.sidebarOpened = !this.sidebarOpened;
  }

  logout(): void {
    this.authService.logout().subscribe({
      next: () => {
        console.log('Logged out successfully');
      }
    });
  }

  onOrderClicked(orderId: number): void {
    console.log('Order clicked:', orderId);
  }

  onMapReady(map: any): void {
    console.log('Map ready:', map);
  }
}
