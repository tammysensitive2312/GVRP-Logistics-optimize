import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { catchError, map } from 'rxjs/operators';
import { forkJoin, Observable, of } from 'rxjs';

import { ApiService } from '@core/services/api.service';
import { DepotStore } from '@core/services/depot.store';
import { VehicleTypeStore } from '@core/services/vehicle-type.store';

export interface SetupStatus {
  hasDepots: boolean;
  hasVehicleTypes: boolean;
  hasFleet: boolean;
  complete: boolean;
}

/**
 * Setup completeness guard
 *
 * Ports V1 `app.js checkSetupStatus()` + `screen-restoration.js`: a user whose
 * branch has no depot / vehicle type / vehicle is pushed into the wizard step
 * that is missing instead of landing on an empty dashboard.
 *
 * Two V1 versions disagreed on the fleet check - `app.js` summed
 * `fleet.vehicle_count` across fleets while `screen-restoration.js` read a
 * non-existent `fleet.vehicleCount` (always undefined, so setup never looked
 * complete). The `app.js` behaviour is the one implemented here.
 *
 * On an API failure the guard lets the navigation through rather than trapping
 * the user in a redirect loop; the screens themselves surface the error.
 */
export const setupGuard: CanActivateFn = (): Observable<boolean | UrlTree> => {
  const router = inject(Router);

  return loadSetupStatus().pipe(
    map(status => {
      if (status.complete) return true;

      if (!status.hasDepots) return router.createUrlTree(['/setup/depot']);
      if (!status.hasVehicleTypes) return router.createUrlTree(['/setup/vehicle-types']);
      return router.createUrlTree(['/setup/fleet']);
    }),
    catchError((error: unknown) => {
      console.error('Setup check failed:', error);
      return of(true);
    })
  );
};

function loadSetupStatus(): Observable<SetupStatus> {
  const api = inject(ApiService);
  const depotStore = inject(DepotStore);
  const vehicleTypeStore = inject(VehicleTypeStore);

  return forkJoin({
    depots: depotStore.load(),
    vehicleTypes: vehicleTypeStore.load(),
    fleets: api.getFleets()
  }).pipe(
    map(({ depots, vehicleTypes, fleets }) => {
      const vehicleCount = (fleets ?? []).reduce(
        (sum, fleet) => sum + (fleet.vehicle_count ?? 0),
        0
      );

      const hasDepots = depots.length > 0;
      const hasVehicleTypes = vehicleTypes.length > 0;
      const hasFleet = vehicleCount > 0;

      return {
        hasDepots,
        hasVehicleTypes,
        hasFleet,
        complete: hasDepots && hasVehicleTypes && hasFleet
      };
    })
  );
}
