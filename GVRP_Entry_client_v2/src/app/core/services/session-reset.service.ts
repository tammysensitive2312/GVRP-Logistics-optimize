import { inject, Injectable, Injector } from '@angular/core';

import { DepotStore } from '@core/services/depot.store';
import { JobPollingService } from '@core/services/job-polling.service';
import { SolutionStore } from '@core/services/solution.store';
import { StateService } from '@core/services/state.service';
import { VehicleTypeStore } from '@core/services/vehicle-type.store';
import { VehicleSelectionService } from '@shared/services/vehicle-selection.service';

/**
 * Session Reset
 *
 * The one place that drops every piece of branch-owned client state. Each
 * branch is a separate company, and the stores below are root singletons that
 * outlive a logout: without this, a planner of branch A who loaded solution 92
 * and then signed in as a planner of branch B in the same tab still saw
 * solution 92 (the SPA was never reloaded, so the stores kept A's data).
 *
 * Called on every logout path and on every successful login, so a new session
 * always starts empty whoever signed in before.
 *
 * The stores are resolved lazily through the injector: this service is injected
 * by AuthService, the auth interceptor and the navbar, and resolving the stores
 * eagerly would pull ApiService/HttpClient into every one of them.
 */
@Injectable({ providedIn: 'root' })
export class SessionResetService {
  private readonly injector = inject(Injector);

  resetBranchState(): void {
    this.injector.get(JobPollingService).stopPolling();
    this.injector.get(SolutionStore).clear();
    this.injector.get(DepotStore).reset();
    this.injector.get(VehicleTypeStore).reset();
    this.injector.get(StateService).reset();
    this.injector.get(VehicleSelectionService).setSelection([]);
  }
}
