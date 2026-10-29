import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import {
  RouteDTO,
  SolutionComplianceDTO,
  SolutionDTO,
  StopDTO
} from '@core/models';
import { LezApiService } from '@core/services/lez-api.service';
import { MapService } from '@shared/services/map.service';

import { CompliancePanelComponent } from './compliance-panel.component';

const stop = (partial: Partial<StopDTO>): StopDTO => ({
  type: 'ORDER',
  location_name: 'Stop',
  latitude: 21,
  longitude: 105,
  arrival_time: '08:30:00',
  load_after: 100,
  wait_time: 0,
  ...partial
});

const route = (id: number, plate: string): RouteDTO => ({
  id,
  vehicle_id: id,
  vehicle_license_plate: plate,
  start_time: '08:00:00',
  end_time: '10:00:00',
  distance: 12,
  service_time: 1,
  order_count: 1,
  load_utilization: 0.5,
  stops: [
    stop({ type: 'DEPOT', location_name: 'Central depot', departure_time: '08:00:00' }),
    stop({ type: 'ORDER', order_id: 1, location_name: 'Order A' }),
    stop({ type: 'DEPOT', location_name: 'Central depot' })
  ]
});

const solution = (): SolutionDTO => ({
  id: 42,
  job_id: 7,
  total_cost: 100,
  total_distance: 24,
  total_time: 2,
  total_co2: 5,
  total_vehicles_used: 2,
  served_orders: 1,
  unserved_orders: 0,
  routes: [route(11, '29A-11111'), route(12, '29A-22222')]
});

/** CP-T1: the compliance panel of LEZ-001 P1c, C7. */
describe('CompliancePanelComponent', () => {
  let fixture: ComponentFixture<CompliancePanelComponent>;
  let component: CompliancePanelComponent;
  let lez: jasmine.SpyObj<LezApiService>;
  let mapService: jasmine.SpyObj<MapService>;

  const compliance = (overrides: Partial<SolutionComplianceDTO> = {}): SolutionComplianceDTO => ({
    solution_id: 42,
    routes: [
      {
        route_id: 11,
        status: 'VIOLATION',
        checked_at: '2027-01-01T08:00:00',
        violations: [{ code: 'ZONE_FORBIDDEN', seq: 1, zone_key: 'Z17', detail: 'Rule 1' }]
      },
      { route_id: 12, status: 'COMPLIANT', checked_at: '2027-01-01T08:00:00', violations: [] }
    ],
    zones: [{ key: 'Z17', branch_zone_id: 17, name: 'Hanoi LEZ' }],
    lez_unassigned: [],
    ...overrides
  });

  const setup = async (
    dto: SolutionComplianceDTO = compliance(),
    loadError = false
  ): Promise<void> => {
    lez = jasmine.createSpyObj<LezApiService>('LezApiService', [
      'solutionCompliance', 'recheckCompliance'
    ]);
    if (loadError) {
      lez.solutionCompliance.and.returnValue(throwError(() => new Error('down')));
    } else {
      lez.solutionCompliance.and.returnValue(of(dto));
    }
    lez.recheckCompliance.and.returnValue(of(dto));
    mapService = jasmine.createSpyObj<MapService>('MapService', ['focusLeg']);

    await TestBed.configureTestingModule({
      imports: [CompliancePanelComponent],
      providers: [
        { provide: LezApiService, useValue: lez },
        { provide: MapService, useValue: mapService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CompliancePanelComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('solution', solution());
    // Explicit load instead of waiting for the constructor effect: the effect
    // then sees the same solution id and does not issue a second request.
    component.load();
    fixture.detectChanges();
  };

  const text = (): string => fixture.nativeElement.textContent as string;

  const buttonByText = (label: string): HTMLButtonElement =>
    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[])
      .find(button => (button.textContent ?? '').trim() === label) as HTMLButtonElement;

  it('renders one status per route with the label of the stored status', async () => {
    await setup();

    expect(lez.solutionCompliance).toHaveBeenCalledWith(42);
    expect(text()).toContain('29A-11111');
    expect(text()).toContain('Violation');
    expect(text()).toContain('29A-22222');
    expect(text()).toContain('Compliant');
    expect(component.rows().length).toBe(2);
  });

  it('names the stop and the zone of a violation through orderStops and zones', async () => {
    await setup();

    const violation = component.rows()[0].violations[0];
    expect(violation.text).toContain('ZONE_FORBIDDEN');
    expect(violation.text).toContain('Order A');
    expect(violation.text).toContain('Hanoi LEZ');
    expect(text()).toContain('ZONE_FORBIDDEN');
  });

  it('focuses the offending leg on the planner map when a violation is clicked', async () => {
    await setup();

    buttonByText('ZONE_FORBIDDEN at Order A in Hanoi LEZ — Rule 1').click();

    // review-p1c R3: the panel now passes the code and detail too.
    expect(mapService.focusLeg).toHaveBeenCalledWith(11, 1, 'ZONE_FORBIDDEN', 'Rule 1');
  });

  it('passes a leg violation through unchanged so the map can tell it from a stop', async () => {
    await setup(compliance({
      routes: [
        {
          route_id: 11,
          status: 'VIOLATION',
          checked_at: '2027-01-01T08:00:00',
          violations: [{
            code: 'LEZ_DATA_MISSING',
            seq: 2,
            zone_key: 'Z17',
            detail: 'Vehicle may not transit through zone Z17'
          }]
        },
        { route_id: 12, status: 'COMPLIANT', violations: [] }
      ]
    }));

    const link = fixture.nativeElement.querySelector('.violation-link') as HTMLButtonElement;
    link.click();

    expect(mapService.focusLeg).toHaveBeenCalledWith(
      11, 2, 'LEZ_DATA_MISSING', 'Vehicle may not transit through zone Z17');
  });

  it('lists the orders left out for low-emission reasons', async () => {
    await setup(compliance({
      lez_unassigned: [{
        order_id: 5,
        order_code: 'ORD-5',
        reason_code: 'NO_LEGAL_VEHICLE',
        reason: 'No vehicle may enter the zone'
      }]
    }));

    expect(text()).toContain('ORD-5');
    expect(text()).toContain('No vehicle may enter the zone');
    expect(component.lezUnassigned().length).toBe(1);
  });

  it('says so when no zone applies and every route is compliant', async () => {
    await setup({
      solution_id: 42,
      routes: [
        { route_id: 11, status: 'COMPLIANT', violations: [] },
        { route_id: 12, status: 'COMPLIANT', violations: [] }
      ],
      zones: [],
      lez_unassigned: []
    });

    expect(component.noZoneApplies()).toBeTrue();
    expect(text()).toContain('No low-emission zone applies to this plan.');
  });

  it('emits the loaded compliance for the route view', async () => {
    await setup();
    const emitted: (SolutionComplianceDTO | null)[] = [];
    component.complianceChange.subscribe(dto => emitted.push(dto));

    component.load();

    expect(emitted.length).toBe(1);
    expect(emitted[0]?.solution_id).toBe(42);
  });

  it('Recheck now POSTs and re-renders the fresh verdict', async () => {
    await setup();
    lez.recheckCompliance.and.returnValue(of(compliance({
      routes: [
        { route_id: 11, status: 'COMPLIANT', checked_at: '2027-01-02T08:00:00', violations: [] },
        { route_id: 12, status: 'COMPLIANT', checked_at: '2027-01-02T08:00:00', violations: [] }
      ]
    })));

    buttonByText('Recheck now').click();
    fixture.detectChanges();

    expect(lez.recheckCompliance).toHaveBeenCalledWith(42);
    expect(component.rows()[0].status).toBe('COMPLIANT');
    expect(text()).not.toContain('Violation');
  });

  it('a failed load leaves an unknown state and emits null', async () => {
    await setup(compliance(), true);
    const emitted: (SolutionComplianceDTO | null)[] = [];
    component.complianceChange.subscribe(dto => emitted.push(dto));

    component.load();
    fixture.detectChanges();

    expect(component.compliance()).toBeNull();
    expect(component.error()).toBe('Compliance is not available right now.');
    expect(emitted).toEqual([null]);
    expect(text()).toContain('Compliance is not available right now.');
  });
});
