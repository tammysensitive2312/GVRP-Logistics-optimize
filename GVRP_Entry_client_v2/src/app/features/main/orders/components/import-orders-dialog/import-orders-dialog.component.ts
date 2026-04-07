import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatRadioModule } from '@angular/material/radio';

export interface ImportDialogResult {
  method: 'file' | 'text';
  deliveryDate: string;
  serviceTime: number;
  overwrite: boolean;
  skipValidationErrors: boolean;
  file?: File;
  textData?: string;
}

@Component({
  selector: 'app-import-orders-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule,
    MatRadioModule
  ],
  templateUrl: './import-orders-dialog.component.html',
  styleUrl: './import-orders-dialog.component.scss'
})
export class ImportOrdersDialogComponent {
  method: 'file' | 'text' = 'file';
  deliveryDate = new Date().toISOString().split('T')[0];
  serviceTime = 5;
  overwrite = false;
  skipValidationErrors = false;
  selectedFile: File | null = null;
  textData = '';
  isDragging = false;

  // Validate
  fileError = '';

  constructor(
    private dialogRef: MatDialogRef<ImportOrdersDialogComponent>
  ) {}

  // ============================================
  // FILE HANDLING
  // ============================================
  onDropZoneClick(fileInput: HTMLInputElement): void {
    fileInput.click();
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragging = true;
  }

  onDragLeave(): void {
    this.isDragging = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragging = false;

    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      this.validateAndSetFile(files[0]);
    }
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.validateAndSetFile(input.files[0]);
    }
  }

  private validateAndSetFile(file: File): void {
    this.fileError = '';

    // Validate size (10MB)
    if (file.size > 10 * 1024 * 1024) {
      this.fileError = 'File size exceeds 10MB limit';
      return;
    }

    // Validate extension
    const ext = file.name.split('.').pop()?.toLowerCase();
    if (!['csv', 'xlsx'].includes(ext || '')) {
      this.fileError = 'Only CSV and Excel files are allowed';
      return;
    }

    this.selectedFile = file;
  }

  removeFile(): void {
    this.selectedFile = null;
    this.fileError = '';
  }

  // ============================================
  // TEMPLATE DOWNLOAD
  // ============================================
  downloadTemplate(): void {
    const csv =
      'orderCode,customerName,customerPhone,address,latitude,longitude,' +
      'demand,serviceTime,timeWindowStart,timeWindowEnd,priority,deliveryNotes\n' +
      'ORD001,John Doe,0901234567,123 Main St,21.028511,105.804817,50.5,5,08:00,12:00,1,Handle with care\n';

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'order_template.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  // ============================================
  // SUBMIT
  // ============================================
  canSubmit(): boolean {
    if (!this.deliveryDate) return false;
    if (this.method === 'file') return !!this.selectedFile;
    if (this.method === 'text') return !!this.textData.trim();
    return false;
  }

  onSubmit(): void {
    if (!this.canSubmit()) return;

    const result: ImportDialogResult = {
      method: this.method,
      deliveryDate: this.deliveryDate,
      serviceTime: this.serviceTime,
      overwrite: this.overwrite,
      skipValidationErrors: this.skipValidationErrors,
      file: this.method === 'file' ? this.selectedFile! : undefined,
      textData: this.method === 'text' ? this.textData : undefined
    };

    this.dialogRef.close(result);
  }

  onCancel(): void {
    this.dialogRef.close(null);
  }
}
