import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ZoneMapComponent } from './zone-map.component';

/**
 * LF-C6 / LF-C7 (LEZ-FIX-001 rework, browser-check.md observations 1 and 2):
 * a double-click adds one point, and the crossing message does not move the
 * buttons. Same pattern as UL-T4.
 */
describe('ZoneMapComponent drawing UX', () => {
  let fixture: ComponentFixture<ZoneMapComponent>;
  let component: ZoneMapComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ZoneMapComponent] }).compileComponents();
    fixture = TestBed.createComponent(ZoneMapComponent);
    component = fixture.componentInstance;
    component.editable = true;
    component.height = '200px';
    fixture.detectChanges();
  });

  const control = (): HTMLElement =>
    (fixture.nativeElement as HTMLElement).querySelector('.zone-draw-control') as HTMLElement;
  const button = (label: string): HTMLButtonElement =>
    Array.from(control().querySelectorAll('button')).find(b => b.textContent === label) as HTMLButtonElement;
  const message = (): HTMLElement => control().querySelector('.zone-draw-message') as HTMLElement;
  const drawBowTie = (): void => {
    component.addPoint(21.0, 105.8);
    component.addPoint(21.1, 105.9);
    component.addPoint(21.0, 105.9);
    component.addPoint(21.1, 105.8);
  };

  it('LF-C6: the same point clicked twice in a row is kept once; one Undo removes it', () => {
    component.startDraw();
    component.addPoint(21.0, 105.8);
    component.addPoint(21.0, 105.9);
    component.addPoint(21.0, 105.9);

    expect(component.points).toEqual([[21.0, 105.8], [21.0, 105.9]]);

    component.undoPoint();
    expect(component.points).toEqual([[21.0, 105.8]]);
  });

  it('LF-C6: returning to an earlier, non-consecutive point is still added', () => {
    component.startDraw();
    component.addPoint(21.0, 105.8);
    component.addPoint(21.0, 105.9);
    component.addPoint(21.0, 105.8);

    expect(component.points.length).toBe(3);
  });

  it('LF-C7: the crossing message sits under the buttons and does not widen the control', () => {
    component.startDraw();
    const widthBefore = control().getBoundingClientRect().width;
    const undoLeftBefore = button('Undo point').getBoundingClientRect().left;

    drawBowTie();

    expect(message().style.display).toBe('block');
    expect(message().getBoundingClientRect().top)
      .toBeGreaterThanOrEqual(button('Finish').getBoundingClientRect().bottom);
    expect(control().getBoundingClientRect().width).toBeCloseTo(widthBefore, 0);
    expect(button('Undo point').getBoundingClientRect().left).toBeCloseTo(undoLeftBefore, 0);
  });
});
