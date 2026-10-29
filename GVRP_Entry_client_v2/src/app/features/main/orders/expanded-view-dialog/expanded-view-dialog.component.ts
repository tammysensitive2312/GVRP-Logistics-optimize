import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

import { SolutionComplianceDTO, SolutionDTO } from '@core/models';
import { CompliancePanelComponent } from '@features/main/orders/lez-compliance-panel/compliance-panel.component';
import { RouteViewComponent } from '@features/main/orders/route-view/route-view.component';
import { TimelineViewComponent } from '@features/main/orders/timeline-view/timeline-view.component';

export interface ExpandedViewDialogData {
  view: 'route' | 'timeline';
  solution: SolutionDTO;
}

/**
 * The Route or Timeline tab content in a large dialog (UX-LEZ-001, D8).
 *
 * `route` wires its own compliance panel to its route view exactly like the
 * tab does. Clicking a violation closes the dialog so the planner sees the
 * highlighted leg on the map behind it; Collapse closes it too.
 */
@Component({
  selector: 'app-expanded-view-dialog',
  standalone: true,
  imports: [
    MatDialogModule,
    MatIconModule,
    MatTooltipModule,
    CompliancePanelComponent,
    RouteViewComponent,
    TimelineViewComponent
  ],
  templateUrl: './expanded-view-dialog.component.html',
  styleUrl: './expanded-view-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ExpandedViewDialogComponent {
  readonly data = inject<ExpandedViewDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<ExpandedViewDialogComponent>>(MatDialogRef);

  /** Compliance of this dialog's panel, handed to its route view. */
  readonly routeCompliance = signal<SolutionComplianceDTO | null>(null);

  get title(): string {
    return this.data.view === 'route' ? 'Route' : 'Timeline';
  }

  collapse(): void {
    this.dialogRef.close();
  }
}
