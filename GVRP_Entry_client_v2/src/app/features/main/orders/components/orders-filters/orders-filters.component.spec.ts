import { ComponentFixture, TestBed } from '@angular/core/testing';

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
});
