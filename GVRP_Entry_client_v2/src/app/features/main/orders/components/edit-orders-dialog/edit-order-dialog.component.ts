import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ApiService } from '@core/services/api.service';
import { ToastService } from '@shared/services/toast.service';
import { OrderDTO, OrderInputDTO } from '@core/models';
import {MatProgressSpinner} from '@angular/material/progress-spinner';

export interface EditOrderDialogData {
  orderId?: number;
}

@Component({
  selector: 'app-edit-order-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinner
  ],
  templateUrl: './edit-order-dialog.component.html',
  styleUrl: './edit-order-dialog.component.scss'
})
export class EditOrderDialogComponent implements OnInit {
  isEditMode = false;
  isLoading = false;
  isSaving = false;

  form: OrderInputDTO & { status?: string; delivery_date?: string } = {
    order_code: '',
    customer_name: '',
    customer_phone: '',
    address: '',
    latitude: 0,
    longitude: 0,
    demand: 0,
    service_time: 5,
    time_window_start: '',
    time_window_end: '',
    priority: 5,
    delivery_notes: '',
    status: 'SCHEDULED',
    delivery_date: new Date().toISOString().split('T')[0]
  };

  get title(): string {
    return this.isEditMode ? '📝 Edit Order' : '➕ Add Order';
  }

  constructor(
    private dialogRef: MatDialogRef<EditOrderDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: EditOrderDialogData,
    private apiService: ApiService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.isEditMode = !!this.data?.orderId;

    if (this.isEditMode) {
      this.loadOrder(this.data.orderId!);
    }
  }

  private loadOrder(orderId: number): void {
    this.isLoading = true;

    this.apiService.getOrderById(orderId)
      .subscribe({
        next: (order) => {
          this.fillForm(order);
          this.isLoading = false;
        },
        error: () => {
          this.toast.error('Failed to load order');
          this.isLoading = false;
          this.dialogRef.close(null);
        }
      });

    console.log('Load order:', orderId);
    this.isLoading = false;
  }

  private fillForm(order: OrderDTO): void {
    this.form = {
      order_code: order.order_code,
      customer_name: order.customer_name,
      customer_phone: order.customer_phone || '',
      address: order.address,
      latitude: order.latitude,
      longitude: order.longitude,
      demand: order.demand,
      service_time: order.service_time,
      time_window_start: order.time_window_start || '',
      time_window_end: order.time_window_end || '',
      priority: order.priority,
      delivery_notes: order.delivery_notes || '',
      status: order.status,
      delivery_date: order.delivery_date
    };
  }

  validate(): string[] {
    const errors: string[] = [];

    if (!this.form.order_code?.trim()) errors.push('Order code is required');
    if (!this.form.customer_name?.trim()) errors.push('Customer name is required');
    if (!this.form.address?.trim()) errors.push('Address is required');
    if (!this.form.latitude || !this.form.longitude) errors.push('Coordinates are required');
    if (!this.form.demand || this.form.demand <= 0) errors.push('Demand must be greater than 0');
    if (this.form.time_window_start && this.form.time_window_end) {
      if (this.form.time_window_start >= this.form.time_window_end) {
        errors.push('Start time must be before end time');
      }
    }

    return errors;
  }

  canSubmit(): boolean {
    return !this.isLoading && !this.isSaving &&
      !!this.form.order_code && !!this.form.customer_name &&
      !!this.form.address && !!this.form.demand;
  }

  onSubmit(): void {
    const errors = this.validate();
    if (errors.length > 0) {
      this.toast.error(errors[0]);
      return;
    }

    if (this.isEditMode) {
      this.isSaving = true;
      this.apiService.updateOrder(this.data.orderId!, this.form)
        .subscribe({
          next: () => {
            this.toast.success('Order updated successfully');
            this.isSaving = false;
            this.dialogRef.close(true);
          },
          error: (err) => {
            this.toast.error(err.message || 'Failed to update order');
            this.isSaving = false;
          }
        });
    } else {
      // Create — làm sau
      this.toast.info('Create order - Coming soon!');
    }
  }

  onCancel(): void {
    this.dialogRef.close(null);
  }

  onPickLocation(): void {
    this.toast.info('Location picker - Coming soon!');
  }
}
