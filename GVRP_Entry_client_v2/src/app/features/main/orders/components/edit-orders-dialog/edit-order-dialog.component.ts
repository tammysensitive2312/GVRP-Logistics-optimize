import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {MatDialogModule, MatDialogRef, MAT_DIALOG_DATA, MatDialog} from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ApiService } from '@core/services/api.service';
import { ToastService } from '@shared/services/toast.service';
import { OrderDTO, VEHICLE_SKILLS } from '@core/models';
import {MatProgressSpinner} from '@angular/material/progress-spinner';
import {MapPickerDialogComponent} from '@features/main/orders/components/map-picker-dialog/map-picker-dialog.component';
import { serverErrorMessage } from '@shared/utils/api-error.utils';
import { localDateIso } from '@shared/utils/date.utils';
import { coordinateValue, isBlankCoordinate, OrderFormModel, toOrderPayload, clearStaleCoordinates } from './order-payload.utils';

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
  readonly skillOptions = VEHICLE_SKILLS;
  isEditMode = false;
  isLoading = false;
  isSaving = false;
  private originalFormSnapshot: string = '';
  private originalOrder: { address: string; latitude: number | null; longitude: number | null } = {
    address: '',
    latitude: null,
    longitude: null
  };

  // Coordinates start empty: the server geocodes the address (UX-ORDERS-001, C1).
  form: OrderFormModel = {
    order_code: '',
    customer_name: '',
    customer_phone: '',
    address: '',
    latitude: null,
    longitude: null,
    demand: 0,
    service_time: 5,
    time_window_start: '',
    time_window_end: '',
    priority: 5,
    delivery_notes: '',
    required_skills: [],
    status: 'SCHEDULED',
    delivery_date: localDateIso()
  };

  get title(): string {
    return this.isEditMode ? '📝 Edit Order' : '➕ Add Order';
  }

  constructor(
    private dialogRef: MatDialogRef<EditOrderDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: EditOrderDialogData,
    private apiService: ApiService,
    private toast: ToastService,
    private dialog: MatDialog
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
      required_skills: order.required_skills ?? [],
      status: order.status,
      delivery_date: order.delivery_date
    };

    this.originalFormSnapshot = JSON.stringify(this.form);
    this.originalOrder = {
      address: order.address ?? '',
      latitude: order.latitude ?? null,
      longitude: order.longitude ?? null
    };
  }

  validate(): string[] {
    const errors: string[] = [];

    if (!this.form.order_code?.trim()) errors.push('Order code is required');
    if (!this.form.customer_name?.trim()) errors.push('Customer name is required');
    if (!this.form.address?.trim()) errors.push('Address is required');
    const latitudeBlank = isBlankCoordinate(this.form.latitude);
    const longitudeBlank = isBlankCoordinate(this.form.longitude);
    if (latitudeBlank !== longitudeBlank) {
      errors.push('Enter both latitude and longitude, or leave both empty.');
    } else if (!latitudeBlank) {
      const latitude = coordinateValue(this.form.latitude);
      const longitude = coordinateValue(this.form.longitude);
      if (latitude === null || latitude < -90 || latitude > 90) {
        errors.push('Latitude must be between -90 and 90');
      }
      if (longitude === null || longitude < -180 || longitude > 180) {
        errors.push('Longitude must be between -180 and 180');
      }
    }
    if (!this.form.demand || this.form.demand <= 0) errors.push('Demand must be greater than 0');
    if (this.form.time_window_start && !this.form.time_window_end) {
      errors.push('Enter both start and end time, or leave both empty.');
    }
    if (!this.form.time_window_start && this.form.time_window_end) {
      errors.push('Enter both start and end time, or leave both empty.');
    }
    if (this.form.time_window_start && this.form.time_window_end) {
      if (this.form.time_window_start >= this.form.time_window_end) {
        errors.push('Start time must be before end time');
      }
    }

    return errors;
  }

  hasChanges(): boolean {
    if (!this.isEditMode) {
      return true
    }

    const currentFormSnapshot = JSON.stringify((this.form));
    return currentFormSnapshot !== this.originalFormSnapshot;
  }

  canSubmit(): boolean {
    return !this.isLoading && !this.isSaving &&
      !!this.form.order_code && !!this.form.customer_name &&
      !!this.form.address && !!this.form.demand &&
      this.hasChanges();
  }

  /** Same rule as the save path: the hint shows exactly when the coordinates will be cleared. */
  get showStaleCoordinateHint(): boolean {
    return this.isEditMode && clearStaleCoordinates(this.form, this.originalOrder) !== this.form;
  }

  onSubmit(): void {
    const errors = this.validate();
    if (errors.length > 0) {
      this.toast.error(errors[0]);
      return;
    }

    if (this.isEditMode) {
      this.isSaving = true;
      const payload = toOrderPayload(clearStaleCoordinates(this.form, this.originalOrder));
      this.apiService.updateOrder(this.data.orderId!, payload)
        .subscribe({
          next: () => {
            this.toast.success('Order updated successfully');
            this.isSaving = false;
            this.dialogRef.close(true);
          },
          error: (err) => {
            this.toast.error(serverErrorMessage(err, 'Failed to update order'));
            this.isSaving = false;
          }
        });
    } else {
      this.isSaving = true;
      const { status, ...createPayload } = toOrderPayload(this.form);
      this.apiService.addOrder(createPayload)
        .subscribe({
          next: () => {
            this.toast.success('Order created successfully');
            this.isSaving = false;
            this.dialogRef.close(true);
          },
          error: (err) => {
            this.toast.error(serverErrorMessage(err, 'Failed to create order'));
            this.isSaving = false;
          }
        });
    }
  }

  onCancel(): void {
    this.dialogRef.close(null);
  }

  onPickLocation(): void {
    const mapDialogRef = this.dialog.open(MapPickerDialogComponent, {
      width: '600px',
      data: {
        lat: this.form.latitude,
        lng: this.form.longitude,
      }
    });

    mapDialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.form.latitude = result.lat;
        this.form.longitude = result.lng;
        this.toast.success('Location updated!');
      }
    })
  }
}
