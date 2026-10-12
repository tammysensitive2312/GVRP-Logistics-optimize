import { Component, Inject, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';

import { DriverDTO, RevisionDTO, RouteSendDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';

export interface SendRouteDialogData {
  routeId: number;
  solutionId: number;
  licensePlate: string;
}

export interface SendRouteDialogResult {
  driverUserId: number;
  solutionId: number;
}

@Component({
  selector: 'app-send-route-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatSelectModule
  ],
  templateUrl: './send-route-dialog.component.html',
  styleUrl: './send-route-dialog.component.scss'
})
export class SendRouteDialogComponent implements OnInit {
  private readonly api = inject(ApiService);

  drivers: DriverDTO[] = [];
  revisions: RevisionDTO[] = [];
  history: RouteSendDTO[] = [];
  loading = true;
  loadError: string | null = null;

  selectedDriverId: number | null = null;
  selectedSolutionId: number;

  constructor(
    private dialogRef: MatDialogRef<SendRouteDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: SendRouteDialogData
  ) {
    this.selectedSolutionId = data.solutionId;
  }

  ngOnInit(): void {
    this.api.listDrivers().subscribe({
      next: drivers => {
        this.drivers = drivers;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.loadError = 'Could not load drivers. Close and retry.';
      }
    });
    this.api.revisionChain(this.data.solutionId).subscribe({
      next: revisions => (this.revisions = revisions),
      error: () => undefined
    });
    this.api.routeSends(this.data.routeId).subscribe({
      next: sends => (this.history = sends),
      error: () => undefined
    });
  }

  get currentAssignee(): string | null {
    const current = this.history.find(send => send.status === 'CURRENT');
    return current ? current.driver_name : null;
  }

  canSubmit(): boolean {
    return !this.loading && this.loadError === null
      && this.selectedDriverId !== null && this.selectedSolutionId !== null;
  }

  onSubmit(): void {
    if (!this.canSubmit() || this.selectedDriverId === null) return;
    const result: SendRouteDialogResult = {
      driverUserId: this.selectedDriverId,
      solutionId: this.selectedSolutionId
    };
    this.dialogRef.close(result);
  }

  onCancel(): void {
    this.dialogRef.close(null);
  }
}
