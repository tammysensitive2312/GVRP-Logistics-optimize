import { Component, EventEmitter, OnDestroy, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, takeUntil } from 'rxjs/operators';

import { OrderFilter } from '@core/models';

@Component({
  selector: 'app-orders-filters',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule
  ],
  templateUrl: './orders-filters.component.html',
  styleUrl: './orders-filters.component.scss'
})
export class OrdersFiltersComponent implements OnInit, OnDestroy {
  @Output() filterChange = new EventEmitter<OrderFilter>();
  @Output() refresh = new EventEmitter<void>();

  filterForm!: FormGroup;
  maxDate: string;

  private destroy$ = new Subject<void>();
  private initialFormValue!: OrderFilter;

  constructor(private fb: FormBuilder) {
    // Set max date to today
    this.maxDate = new Date().toISOString().split('T')[0];
    this.initForm();
  }

  ngOnInit(): void {
    this.setupFilterListener();
    this.saveInitialFormValue();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initForm(): void {
    const today = new Date().toISOString().split('T')[0];

    this.filterForm = this.fb.group({
      date: [today],
      status: [''],
      search: ['']
    });
  }

  private saveInitialFormValue(): void {
    this.initialFormValue = this.filterForm.value;
  }

  private setupFilterListener(): void {
    this.filterForm.valueChanges.pipe(
      debounceTime(300),
      distinctUntilChanged((prev, curr) =>
        JSON.stringify(prev) === JSON.stringify(curr)
      ),
      takeUntil(this.destroy$)
    ).subscribe(values => {
      this.filterChange.emit(this.buildFilterObject(values));
    });
  }

  /**
   * Clear all filters except date
   */
  clearFilters(): void {
    this.filterForm.patchValue({
      status: '',
      search: ''
    }, { emitEvent: true });
  }

  /**
   * Clear search field only
   */
  clearSearch(): void {
    this.filterForm.patchValue({
      search: ''
    }, { emitEvent: true });
  }

  /**
   * Trigger refresh (reload data from server)
   */
  onRefresh(): void {
    this.refresh.emit();
  }

  /**
   * Check if any filter is active (excluding date)
   */
  hasActiveFilters(): boolean {
    const values = this.filterForm.value;
    return !!(values.status || values.search);
  }

  /**
   * Build filter object from form values
   */
  private buildFilterObject(formValues: any): OrderFilter {
    const filter: OrderFilter = {
      date: formValues.date || new Date().toISOString().split('T')[0]
    };

    if (formValues.status) {
      filter.status = formValues.status;
    }

    if (formValues.search && formValues.search.trim()) {
      filter.search = formValues.search.trim();
    }

    return filter;
  }

  /**
   * Get count of active filters
   */
  getActiveFilterCount(): number {
    const values = this.filterForm.value;
    let count = 0;

    if (values.status) count++;
    if (values.search) count++;

    return count;
  }
}
