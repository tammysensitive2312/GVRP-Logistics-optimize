import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';

import { OrderStatus } from '@core/models';

export interface BulkStatusDialogData {
  selectedCount: number;
}

export interface BulkStatusDialogResult {
  status: OrderStatus;
}

const STATUSES: OrderStatus[] = [
  'SCHEDULED', 'ON_ROUTE', 'SERVICING', 'COMPLETED', 'UNASSIGNED', 'REJECTED'
];

@Component({
  selector: 'app-bulk-status-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatSelectModule
  ],
  templateUrl: './bulk-status-dialog.component.html',
  styleUrl: './bulk-status-dialog.component.scss'
})
export class BulkStatusDialogComponent {
  readonly statuses = STATUSES;
  selectedStatus: OrderStatus | null = null;

  constructor(
    private dialogRef: MatDialogRef<BulkStatusDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: BulkStatusDialogData
  ) {}

  canSubmit(): boolean {
    return this.selectedStatus !== null;
  }

  onSubmit(): void {
    if (!this.canSubmit() || this.selectedStatus === null) return;
    const result: BulkStatusDialogResult = { status: this.selectedStatus };
    this.dialogRef.close(result);
  }

  onCancel(): void {
    this.dialogRef.close(null);
  }
}
