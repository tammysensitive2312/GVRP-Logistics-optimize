import { ComponentFixture, TestBed } from '@angular/core/testing';
import * as L from 'leaflet';

import { GeoJsonPolygon } from '../zone-draw.utils';
import { ZoneMapComponent } from './zone-map.component';

/**
 * LF-C2 / LF-C3 (LEZ-FIX-001, D1/D2): double-click zoom and crossing outlines
 * while drawing. Same pattern as UL-T4: the real component, the draft driven
 * through its own methods; the private map is read with bracket access.
 */
describe('ZoneMapComponent drawing fixes', () => {
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

  const map = (): L.Map => component['map'] as L.Map;
  const control = (): HTMLElement =>
    (fixture.nativeElement as HTMLElement).querySelector('.zone-draw-control') as HTMLElement;
  const finishButton = (): HTMLButtonElement =>
    Array.from(control().querySelectorAll('button')).find(b => b.textContent === 'Finish') as HTMLButtonElement;
  const message = (): HTMLElement => control().querySelector('.zone-draw-message') as HTMLElement;

  it('LF-C2: double-click zoom is off while drawing and back on after Cancel', () => {
    expect(map()).toBeDefined();
    expect(map().doubleClickZoom.enabled()).toBeTrue();

    component.startDraw();
    expect(map().doubleClickZoom.enabled()).toBeFalse();

    component.cancelDraw();
    expect(map().doubleClickZoom.enabled()).toBeTrue();
  });

  it('LF-C2: double-click zoom is back on after Finish', () => {
    component.startDraw();
    component.addPoint(21.0, 105.8);
    component.addPoint(21.0, 105.9);
    component.addPoint(21.1, 105.9);

    component.finishDraw();

    expect(component.drawing).toBeFalse();
    expect(map().doubleClickZoom.enabled()).toBeTrue();
  });

  it('LF-C3: a bow tie cannot be finished and says why; undo to a triangle recovers', () => {
    const emitted: GeoJsonPolygon[] = [];
    component.polygonDrawn.subscribe(polygon => emitted.push(polygon));
    expect(message().style.display).toBe('none');

    component.startDraw();
    component.addPoint(21.0, 105.8);
    component.addPoint(21.1, 105.9);
    component.addPoint(21.0, 105.9);
    component.addPoint(21.1, 105.8);

    expect(finishButton().disabled).toBeTrue();
    expect(message().style.display).not.toBe('none');
    expect(message().textContent).toContain('Edges cross');
    component.finishDraw();
    expect(emitted).toEqual([]);
    expect(component.drawing).toBeTrue();

    component.undoPoint();

    expect(finishButton().disabled).toBeFalse();
    expect(message().style.display).toBe('none');
    finishButton().click();
    expect(emitted.length).toBe(1);
  });

  it('LF-C3: the message is hidden again after Cancel', () => {
    component.startDraw();
    component.addPoint(21.0, 105.8);
    component.addPoint(21.1, 105.9);
    component.addPoint(21.0, 105.9);
    component.addPoint(21.1, 105.8);
    expect(message().style.display).not.toBe('none');

    component.cancelDraw();

    expect(message().style.display).toBe('none');
  });
});
