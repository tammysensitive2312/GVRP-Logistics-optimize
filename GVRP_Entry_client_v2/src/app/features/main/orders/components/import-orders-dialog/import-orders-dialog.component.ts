import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatRadioModule } from '@angular/material/radio';

import { localDateIso } from '@shared/utils/date.utils';

/**
 * CSV template the server's parser reads (UX-ORDERS-001, C3): 13 columns;
 * latitude/longitude are optional (both or neither), otherwise the address is
 * geocoded. `requiredSkills` is `;`-separated.
 */
export const ORDER_IMPORT_TEMPLATE_CSV =
  'orderCode,customerName,customerPhone,address,latitude,longitude,' +
  'demand,serviceTime,timeWindowStart,timeWindowEnd,priority,deliveryNotes,requiredSkills\n' +
  'ORD001,Nguyen Van A,0901234567,"1 Trang Tien, Hoan Kiem, Ha Noi",21.0245,105.8572,' +
  '25.5,10,08:00,11:00,1,Call first,REFRIGERATED;FRAGILE_CAPABLE\n' +
  'ORD002,Tran Thi B,,"2 Hang Bai, Hoan Kiem, Ha Noi",,,12,5,,,2,,\n';

/** Extensions the server's import parsers accept. */
const ACCEPTED_EXTENSIONS = ['csv', 'json'];

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
  deliveryDate = localDateIso();
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
    if (!ACCEPTED_EXTENSIONS.includes(ext || '')) {
      this.fileError = 'Only CSV and JSON files are allowed';
      return;
    }

    this.selectedFile = file;
  }

  removeFile(): void {
    this.selectedFile = null;
    this.fileError = '';
  }

  downloadTemplate(): void {
    const blob = new Blob([ORDER_IMPORT_TEMPLATE_CSV], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'order_template.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

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
