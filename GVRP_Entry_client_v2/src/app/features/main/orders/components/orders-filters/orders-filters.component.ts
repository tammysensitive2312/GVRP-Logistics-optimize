import {Component, EventEmitter, OnDestroy, OnInit, Output} from '@angular/core';
import {OrderFilter} from '@core/models';
import {FormBuilder, FormGroup, ReactiveFormsModule} from '@angular/forms';
import {Subject} from 'rxjs';
import {debounceTime, distinctUntilChanged, takeUntil} from 'rxjs/operators';
import {CommonModule} from '@angular/common';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInput, MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatIconModule} from '@angular/material/icon';
import {MatButtonModule} from '@angular/material/button';


@Component({
  selector: 'app-orders-filters',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatButtonModule
  ],
  templateUrl: './orders-filters.component.html',
  styleUrl: './orders-filters.component.scss'
})
export class OrdersFiltersComponent implements OnInit, OnDestroy{
  @Output() filterChange = new EventEmitter<OrderFilter>();

  filterForm!: FormGroup;
  private destroy$ = new Subject<void>();

  constructor(private fb: FormBuilder) {
    this.initForm();
  }

  ngOnInit() {
    this.setupFilterListener();
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initForm() {
    this.filterForm = this.fb.group({
      date: [new Date().toString().split('T')[0]],
      status: [''],
      priority: [''],
      search: ['']
    });
  }

  private setupFilterListener() {
    this.filterForm.valueChanges.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(values => {
      this.filterChange.emit(values as OrderFilter);
    })
  }

  clearFilters() {
    this.filterForm.patchValue({
      status: '',
      priority: '',
      search: '',
    });
  }


}
