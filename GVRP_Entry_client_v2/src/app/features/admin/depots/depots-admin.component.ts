import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  OnInit
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { DepotStore } from '@core/services/depot.store';

/**
 * Depots management
 *
 * V1's `#depots-section` was an empty "Coming soon..." placeholder and
 * `AdminSettings.switchSection('depots')` had an empty case. This keeps that
 * scope but at least lists the depots that exist and links to depot setup,
 * so the section is not a dead end. Full depot CRUD needs PUT/DELETE /depots.
 */
@Component({
  selector: 'app-depots-admin',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './depots-admin.component.html',
  styleUrl: './depots-admin.component.scss'
})
export class DepotsAdminComponent implements OnInit {
  private readonly store = inject(DepotStore);
  private readonly destroyRef = inject(DestroyRef);

  readonly depots = this.store.depots;
  readonly loading = this.store.loading;
  readonly isEmpty = computed(() => this.depots().length === 0);

  ngOnInit(): void {
    this.store.load().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      error: (error: unknown) => console.error('Failed to load depots:', error)
    });
  }
}
