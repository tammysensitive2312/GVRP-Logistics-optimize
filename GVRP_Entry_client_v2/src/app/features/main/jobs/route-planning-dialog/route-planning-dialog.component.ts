import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';

export interface RoutePlanningDialogData {
  selectedOrdersCount: number;
  selectedVehiclesCount: number;
  totalDemand: number;
}

export interface RoutePlanningDialogResult {
  optimizationGoal: string;
  optimizationSpeed: string;
  timeWindowMode: string;
  allowUnassigned: boolean;
  enablePareto: boolean;
}

@Component({
  selector: 'app-route-planning-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatSelectModule,
    MatCheckboxModule,
    MatIconModule
  ],
  templateUrl: './route-planning-dialog.component.html',
  styleUrl: './route-planning-dialog.component.scss'
})
export class RoutePlanningDialogComponent {
  form: RoutePlanningDialogResult = {
    optimizationGoal: 'MINIMIZE_COST',
    optimizationSpeed: 'NORMAL',
    timeWindowMode: 'STRICT',
    allowUnassigned: false,
    enablePareto: false
  };

  estimatedTimeMap: Record<string, string> = {
    FAST: '2-3 minutes',
    NORMAL: '5-8 minutes',
    HIGH_QUALITY: '10-15 minutes'
  };

  get estimatedTime(): string {
    return this.estimatedTimeMap[this.form.optimizationSpeed];
  }

  constructor(
    private dialogRef: MatDialogRef<RoutePlanningDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: RoutePlanningDialogData
  ) {}

  onSubmit(): void {
    this.dialogRef.close(this.form);
  }

  onCancel(): void {
    this.dialogRef.close(null);
  }
}
