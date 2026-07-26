import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

export interface ConfirmDialogData {
  title: string;
  message?: string;
  confirmText?: string;
  cancelText?: string;
  /** Styles the confirm button as destructive. */
  danger?: boolean;
}

/**
 * Replacement for V1's native `confirm()` calls (vehicle-card.js,
 * vehicle-manager.js, vehicle-type-manager.js).
 * Resolves to `true` only when the user confirms.
 */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>

    @if (data.message) {
      <mat-dialog-content>
        <p class="confirm-message">{{ data.message }}</p>
      </mat-dialog-content>
    }

    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="dialogRef.close(false)">
        {{ data.cancelText ?? 'Hủy' }}
      </button>
      <button
        mat-raised-button
        type="button"
        [color]="data.danger ? 'warn' : 'primary'"
        (click)="dialogRef.close(true)">
        {{ data.confirmText ?? 'Xác nhận' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .confirm-message {
      margin: 0;
      white-space: pre-line;
    }
  `
})
export class ConfirmDialogComponent {
  readonly dialogRef = inject<MatDialogRef<ConfirmDialogComponent, boolean>>(MatDialogRef);
  readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
}
