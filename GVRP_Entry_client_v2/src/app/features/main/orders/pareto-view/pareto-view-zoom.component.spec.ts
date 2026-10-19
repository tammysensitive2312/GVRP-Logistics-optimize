import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';

import {
  ParetoCandidateDTO,
  ParetoFrontierDTO,
  SolutionDTO
} from '@core/models';
import { ApiService } from '@core/services/api.service';
import { SolutionStore } from '@core/services/solution.store';
import { ConfirmService } from '@shared/services/confirm.service';
import { ToastService } from '@shared/services/toast.service';
import { windowSpan } from '@shared/utils/pareto-chart.utils';

import { ParetoViewComponent } from './pareto-view.component';

const candidate = (
  partial: Partial<ParetoCandidateDTO> & { id: number; index: number }
): ParetoCandidateDTO => ({
  label: `K${partial.index}`,
  cost_weight: 0.5,
  co2_weight: 0.5,
  total_cost: 1_000_000,
  total_distance: 80,
  total_co2: 12,
  total_time: 6,
  total_vehicles_used: 3,
  served_orders: 120,
  unserved_orders: 0,
  frontier_status: 'FRONTIER',
  engine_selected: false,
  applicable: true,
  not_applicable_reason: null,
  ...partial
});

const solution: SolutionDTO = {
  id: 9,
  job_id: 4,
  total_cost: 1_000_000,
  total_distance: 80,
  total_time: 6,
  total_co2: 12,
  total_vehicles_used: 3,
  served_orders: 120,
  unserved_orders: 0,
  routes: []
};

const makeFrontier = (): ParetoFrontierDTO => ({
  job_id: 4,
  solution_id: 9,
  latest_solution_id: 10,
  active_candidate_id: 2,
  clustered: true,
  cluster_count: 7,
  candidates: [
    candidate({ id: 1, index: 0, total_cost: 900_000, total_co2: 14 }),
    candidate({ id: 2, index: 1, total_cost: 1_000_000, total_co2: 12,
      applicable: false, not_applicable_reason: 'ALREADY_ACTIVE' }),
    candidate({ id: 3, index: 2, total_cost: 1_100_000, total_co2: 10, engine_selected: true }),
    candidate({ id: 4, index: 3, total_cost: 1_200_000, total_co2: 15,
      frontier_status: 'DOMINATED', applicable: false, not_applicable_reason: 'NOT_ON_FRONTIER' }),
    candidate({ id: 5, index: 4, total_cost: 800_000, total_co2: 9, served_orders: 100,
      frontier_status: 'SERVICE_DOMINATED', applicable: false,
      not_applicable_reason: 'NOT_ON_FRONTIER' })
  ]
});

const pointer = (
  type: string,
  id: number,
  x = 0,
  y = 0
): PointerEvent => new PointerEvent(type, {
  pointerId: id,
  clientX: x,
  clientY: y,
  bubbles: true,
  cancelable: true
});

/** Z-T6..Z-T10: component-level zoom / pan / pinch interactions (PARETO-003). */
describe('ParetoViewComponent zoom and pan', () => {
  let fixture: ComponentFixture<ParetoViewComponent>;
  let component: ParetoViewComponent;
  let host: HTMLElement;
  let svg: SVGSVGElement;

  let api: jasmine.SpyObj<ApiService>;
  let store: jasmine.SpyObj<SolutionStore>;
  let confirm: jasmine.SpyObj<ConfirmService>;
  let toast: jasmine.SpyObj<ToastService>;

  /** Map a viewBox coordinate onto client coordinates through the real SVG layout. */
  const toClient = (vx: number, vy: number): { clientX: number; clientY: number } => {
    const ctm = svg.getScreenCTM();
    expect(ctm).not.toBeNull();
    const point = svg.createSVGPoint();
    point.x = vx;
    point.y = vy;
    const mapped = point.matrixTransform(ctm as DOMMatrix);
    return { clientX: mapped.x, clientY: mapped.y };
  };

  const labels = (): string[] =>
    Array.from(host.querySelectorAll('text.x-label')).map(el => el.textContent ?? '');

  beforeEach(async () => {
    api = jasmine.createSpyObj<ApiService>('ApiService',
      ['applyParetoCandidate', 'latestRevisionId']);
    store = jasmine.createSpyObj<SolutionStore>('SolutionStore',
      ['setSolution', 'loadById']);
    confirm = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['ask']);
    confirm.ask.and.returnValue(of(true));
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);

    await TestBed.configureTestingModule({
      imports: [ParetoViewComponent, NoopAnimationsModule],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: SolutionStore, useValue: store },
        { provide: ConfirmService, useValue: confirm },
        { provide: ToastService, useValue: toast }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ParetoViewComponent);
    fixture.componentRef.setInput('solution', solution);
    fixture.componentRef.setInput('frontier', makeFrontier());
    component = fixture.componentInstance;
    host = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    TestBed.flushEffects();

    svg = host.querySelector('svg.pareto-chart') as SVGSVGElement;
    expect(svg).not.toBeNull();
  });

  it('renders an accessible zoom control strip with the documented disabled states', () => {
    const strip = host.querySelector('.chart-controls[role="group"]');
    expect(strip?.getAttribute('aria-label')).toBe('Chart zoom');

    const zoomIn = host.querySelector('.zoom-in') as HTMLButtonElement;
    const zoomOut = host.querySelector('.zoom-out') as HTMLButtonElement;
    const reset = host.querySelector('.zoom-reset') as HTMLButtonElement;

    expect(zoomIn.getAttribute('aria-label')).toBe('Zoom in');
    expect(zoomIn.getAttribute('title')).toBe('Zoom in');
    expect(zoomOut.getAttribute('aria-label')).toBe('Zoom out');
    expect(zoomOut.getAttribute('title')).toBe('Zoom out');
    expect(reset.getAttribute('aria-label')).toBe('Reset zoom');
    expect(reset.getAttribute('title')).toBe('Reset zoom');

    expect(zoomIn.disabled).toBeFalse();
    expect(zoomOut.disabled).toBeTrue();
    expect(reset.disabled).toBeTrue();
  });

  it('zooms in about the pointer on wheel and changes the tick labels', () => {
    const before = labels();
    const center = toClient(300, 142);

    const event = new WheelEvent('wheel', {
      deltaY: -100,
      clientX: center.clientX,
      clientY: center.clientY,
      bubbles: true,
      cancelable: true
    });
    svg.dispatchEvent(event);
    fixture.detectChanges();

    expect(event.defaultPrevented).toBeTrue();
    const zoomed = component.window();
    const full = component.fullWindow();
    expect(zoomed).not.toBeNull();
    expect(full).not.toBeNull();
    expect(windowSpan(zoomed!).x).toBeLessThan(windowSpan(full!).x);
    expect(labels()).not.toEqual(before);
    expect(labels().length).toBeGreaterThan(0);
  });

  it('ignores the wheel outside the plot rectangle and does not prevent default', () => {
    const margin = toClient(10, 150);

    const event = new WheelEvent('wheel', {
      deltaY: -100,
      clientX: margin.clientX,
      clientY: margin.clientY,
      bubbles: true,
      cancelable: true
    });
    svg.dispatchEvent(event);
    fixture.detectChanges();

    expect(event.defaultPrevented).toBeFalse();
    expect(component.zoom()).toBeNull();
  });

  it('restores the default view on Reset and disables Reset while at the default', () => {
    const reset = (): HTMLButtonElement =>
      host.querySelector('.zoom-reset') as HTMLButtonElement;

    expect(reset().disabled).toBeTrue();

    component.zoomIn();
    fixture.detectChanges();

    expect(component.isDefaultView()).toBeFalse();
    expect(reset().disabled).toBeFalse();

    reset().click();
    fixture.detectChanges();

    expect(component.zoom()).toBeNull();
    expect(component.isDefaultView()).toBeTrue();
    expect(reset().disabled).toBeTrue();
  });

  it('disables Zoom in once the maximum magnification is reached', () => {
    for (let i = 0; i < 20; i++) {
      component.zoomIn();
    }
    fixture.detectChanges();

    expect(component.atMaxZoom()).toBeTrue();
    expect((host.querySelector('.zoom-in') as HTMLButtonElement).disabled).toBeTrue();
    expect((host.querySelector('.zoom-out') as HTMLButtonElement).disabled).toBeFalse();
  });

  it('suppresses selection after a drag but still selects on a plain click', () => {
    const point1 = host.querySelector('.point[data-candidate-id="1"]') as Element;
    const point3 = host.querySelector('.point[data-candidate-id="3"]') as Element;
    const center = toClient(300, 142);

    // Plain press + click (no movement) selects.
    svg.dispatchEvent(pointer('pointerdown', 1, center.clientX, center.clientY));
    point1.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    document.dispatchEvent(pointer('pointerup', 1));
    fixture.detectChanges();
    expect(component.selectedId()).toBe(1);

    // A drag beyond the threshold must not select the point it ends on.
    const far = toClient(330, 162);
    svg.dispatchEvent(pointer('pointerdown', 2, center.clientX, center.clientY));
    document.dispatchEvent(pointer('pointermove', 2, far.clientX, far.clientY));
    point3.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    document.dispatchEvent(pointer('pointerup', 2));
    fixture.detectChanges();

    expect(component.selectedId()).toBe(1);
  });

  it('zooms with a two-pointer pinch about the midpoint', () => {
    const p1 = toClient(250, 142);
    const p2 = toClient(350, 142);

    svg.dispatchEvent(pointer('pointerdown', 1, p1.clientX, p1.clientY));
    svg.dispatchEvent(pointer('pointerdown', 2, p2.clientX, p2.clientY));

    const before = windowSpan(component.window()!).x;

    const p2Far = toClient(450, 142);
    document.dispatchEvent(pointer('pointermove', 2, p2Far.clientX, p2Far.clientY));
    fixture.detectChanges();

    expect(windowSpan(component.window()!).x).toBeLessThan(before);

    document.dispatchEvent(pointer('pointerup', 2));
    document.dispatchEvent(pointer('pointerup', 1));
  });

  it('resets the zoom when the frontier input changes', () => {
    component.zoomIn();
    fixture.detectChanges();
    expect(component.zoom()).not.toBeNull();

    fixture.componentRef.setInput('frontier', makeFrontier());
    TestBed.flushEffects();
    fixture.detectChanges();

    expect(component.zoom()).toBeNull();
    expect(component.isDefaultView()).toBeTrue();
    const win = component.window();
    const full = component.fullWindow();
    expect(win).not.toBeNull();
    expect(windowSpan(win!)).toEqual(windowSpan(full!));
  });
});
