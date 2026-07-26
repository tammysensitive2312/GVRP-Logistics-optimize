// src/app/app.routes.ts
import {Routes} from '@angular/router';
import {AuthGuard} from '@core/guards/auth.guard';
import {setupGuard} from '@core/guards/setup.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login.component')
        .then(m => m.LoginComponent)
  },
  {
    path: 'setup',
    canActivate: [AuthGuard],
    children: [
      {
        path: 'depot',
        loadComponent: () =>
          import('./features/depot/depot-setup/depot-setup.component')
            .then(m => m.DepotSetupComponent)
      },
      {
        path: 'vehicle-types',
        loadComponent: () =>
          import('./features/vehicle-type/vehicle-type-setup/vehicle-type-setup.component')
            .then(m => m.VehicleTypeSetupComponent)
      },
      {
        path: 'fleet',
        loadComponent: () =>
          import('./features/fleet/fleet-setup/fleet-setup.component')
            .then(m => m.FleetSetupComponent)
      },
      {
        path: '',
        redirectTo: 'depot',
        pathMatch: 'full'
      },
      {
        path: '**',
        redirectTo: 'depot'
      }
    ]
  },
  {
    path: 'admin',
    canActivate: [AuthGuard, setupGuard],
    loadComponent: () =>
      import('./features/admin/admin-layout/admin-layout.component')
        .then(m => m.AdminLayoutComponent),
    children: [
      {
        path: 'vehicle-types',
        loadComponent: () =>
          import('./features/admin/vehicle-types/vehicle-types-admin.component')
            .then(m => m.VehicleTypesAdminComponent)
      },
      {
        path: 'vehicles',
        loadComponent: () =>
          import('./features/admin/vehicles/vehicles-admin.component')
            .then(m => m.VehiclesAdminComponent)
      },
      {
        path: 'depots',
        loadComponent: () =>
          import('./features/admin/depots/depots-admin.component')
            .then(m => m.DepotsAdminComponent)
      },
      {
        path: '',
        redirectTo: 'vehicle-types',
        pathMatch: 'full'
      },
      {
        path: '**',
        redirectTo: 'vehicle-types'
      }
    ]
  },
  {
    path: 'main',
    loadComponent: () =>
      import('./layouts/main-layout/main-layout.component')
        .then(m => m.MainLayoutComponent),
    canActivate: [AuthGuard, setupGuard],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/main/main.component')
            .then(m => m.MainComponent)
      }
    ]
  },
  {
    path: '',
    redirectTo: '/login',
    pathMatch: 'full'
  },
  {
    path: '**',
    redirectTo: '/login'
  }
];
