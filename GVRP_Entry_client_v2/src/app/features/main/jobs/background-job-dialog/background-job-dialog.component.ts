import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

import { JobDTO } from '@core/models';
import { StorageService } from '@core/services/storage.service';

export interface BackgroundJobDialogData {
  job: JobDTO;
}

export type BackgroundJobDialogResult = 'view-history' | null;

/**
 * Background Job dialog
 *
 * Ports V1 `background-job-modal.js` + `#modal-background-job`, shown after
 * submitting a NORMAL / HIGH_QUALITY optimization. V2 had replaced it with a
 * single toast, which dropped the job id, the notification address and the
 * "View Job History" action.
 *
 * V1 read the address from `getCurrentUser()?.email`; the stored session only
 * keeps id / username / role, so the username is shown when it looks like an
 * email and a neutral fallback otherwise.
 */
@Component({
  selector: 'app-background-job-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule],
  templateUrl: './background-job-dialog.component.html',
  styleUrl: './background-job-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BackgroundJobDialogComponent {
  readonly dialogRef =
    inject<MatDialogRef<BackgroundJobDialogComponent, BackgroundJobDialogResult>>(
      MatDialogRef
    );
  private readonly data = inject<BackgroundJobDialogData>(MAT_DIALOG_DATA);
  private readonly storage = inject(StorageService);

  readonly job = this.data.job;
  readonly notificationTarget = this.resolveNotificationTarget();

  onViewHistory(): void {
    this.dialogRef.close('view-history');
  }

  private resolveNotificationTarget(): string {
    const username = this.storage.getUser()?.username ?? '';
    return username.includes('@') ? username : 'your registered email';
  }
}
