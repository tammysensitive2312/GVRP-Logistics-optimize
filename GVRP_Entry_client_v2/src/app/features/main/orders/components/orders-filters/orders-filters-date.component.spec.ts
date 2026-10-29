import { ComponentFixture, TestBed } from '@angular/core/testing';

import { localDateIso } from '@shared/utils/date.utils';

import { OrdersFiltersComponent } from './orders-filters.component';

/**
 * MO-T3 of MAP-ORDERS-001. New spec file: the accepted
 * `orders-filters.component.spec.ts` keeps its own assertions unchanged.
 */
describe('OrdersFiltersComponent local date', () => {
  let fixture: ComponentFixture<OrdersFiltersComponent>;
  let component: OrdersFiltersComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrdersFiltersComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(OrdersFiltersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('MO-T3: default date and maxDate are the local today', () => {
    const today = localDateIso();

    expect(component.filterForm.value.date).toBe(today);
    expect(component.maxDate).toBe(today);
  });
});
