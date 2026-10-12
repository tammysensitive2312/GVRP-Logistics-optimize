import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';

import { OrderFilter } from '@core/models';

import { OrdersFiltersComponent } from './orders-filters.component';

describe('OrdersFiltersComponent', () => {
  let component: OrdersFiltersComponent;
  let fixture: ComponentFixture<OrdersFiltersComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrdersFiltersComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(OrdersFiltersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('omits the date when the picker is cleared (no silent today fallback)', fakeAsync(() => {
    const emitted: OrderFilter[] = [];
    component.filterChange.subscribe(filter => emitted.push(filter));

    component.filterForm.setValue({ date: null, status: '', search: '' });
    tick(400);

    expect(emitted.length).toBeGreaterThan(0);
    expect(emitted[emitted.length - 1].date).toBeUndefined();
  }));

  it('keeps an explicitly set date', fakeAsync(() => {
    const emitted: OrderFilter[] = [];
    component.filterChange.subscribe(filter => emitted.push(filter));

    component.filterForm.setValue({ date: '2026-10-11', status: '', search: '' });
    tick(400);

    expect(emitted[emitted.length - 1].date).toBe('2026-10-11');
  }));
});
