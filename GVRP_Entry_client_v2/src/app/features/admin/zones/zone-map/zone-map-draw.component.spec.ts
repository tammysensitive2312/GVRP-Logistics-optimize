import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GeoJsonPolygon } from '../zone-draw.utils';
import { ZoneMapComponent } from './zone-map.component';

/**
 * UL-T4 (UX-LEZ-001, D2): the drawing control exists only when editable.
 * Leaflet map clicks are impractical in Karma; the draft is driven through
 * the component's own methods, which the control buttons call.
 */
describe('ZoneMapComponent drawing', () => {
  let fixture: ComponentFixture<ZoneMapComponent>;
  let component: ZoneMapComponent;

  const create = async (editable: boolean): Promise<void> => {
    await TestBed.configureTestingModule({ imports: [ZoneMapComponent] }).compileComponents();
    fixture = TestBed.createComponent(ZoneMapComponent);
    component = fixture.componentInstance;
    component.editable = editable;
    component.height = '200px';
    fixture.detectChanges();
  };

  const control = (): HTMLElement | null =>
    (fixture.nativeElement as HTMLElement).querySelector('.zone-draw-control');

  it('UL-T4: read-only by default: no draw control', async () => {
    await create(false);

    expect(component.editable).toBeFalse();
    expect(control()).toBeNull();
  });

  it('UL-T4: editable renders Draw, Undo point, Finish and Cancel', async () => {
    await create(true);

    const labels = Array.from(control()?.querySelectorAll('button') ?? []).map(b => b.textContent);
    expect(labels).toEqual(['Draw', 'Undo point', 'Finish', 'Cancel']);
  });

  it('UL-T4: Finish is enabled with 3 points and emits the polygon', async () => {
    await create(true);
    const emitted: GeoJsonPolygon[] = [];
    component.polygonDrawn.subscribe(polygon => emitted.push(polygon));
    const finish = Array.from(control()!.querySelectorAll('button'))
      .find(b => b.textContent === 'Finish') as HTMLButtonElement;

    component.startDraw();
    component.addPoint(21.0, 105.8);
    component.addPoint(21.0, 105.9);
    expect(finish.disabled).toBeTrue();
    component.addPoint(21.1, 105.9);
    expect(finish.disabled).toBeFalse();

    finish.click();

    expect(emitted.length).toBe(1);
    expect(emitted[0].coordinates[0].length).toBe(4);
    expect(component.drawing).toBeFalse();
  });

  it('UL-T4: Undo point and Cancel edit the draft', async () => {
    await create(true);

    component.startDraw();
    component.addPoint(21.0, 105.8);
    component.addPoint(21.0, 105.9);
    component.undoPoint();
    expect(component.points.length).toBe(1);

    component.cancelDraw();
    expect(component.drawing).toBeFalse();
    expect(component.points).toEqual([]);
  });
});
