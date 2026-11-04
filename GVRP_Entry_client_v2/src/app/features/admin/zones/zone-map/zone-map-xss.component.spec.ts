import { ComponentFixture, TestBed } from '@angular/core/testing';
import * as L from 'leaflet';

import { DepotDTO } from '@core/models';

import { ZoneMapComponent } from './zone-map.component';

/**
 * XS-T4 (SEC-XSS-001, D2): the runtime-confirmed sink of SEC-015. A depot
 * name is rendered as text in its marker tooltip on the zone map.
 */
describe('ZoneMapComponent depot tooltip', () => {
  const HOSTILE = '<img src=x onerror="window.__x=1">';

  let fixture: ComponentFixture<ZoneMapComponent>;
  let component: ZoneMapComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ZoneMapComponent] }).compileComponents();
    fixture = TestBed.createComponent(ZoneMapComponent);
    component = fixture.componentInstance;
    component.height = '200px';
    component.depots = [
      { id: 9005, name: HOSTILE, latitude: 21.03, longitude: 105.8 } as unknown as DepotDTO
    ];
    fixture.detectChanges();
  });

  it('XS-T4: no element is injected from a hostile depot name', () => {
    const layer = (component as unknown as { depotLayer?: L.LayerGroup }).depotLayer;
    const marker = layer?.getLayers()[0] as L.Marker | undefined;
    expect(marker).toBeDefined();

    marker!.openTooltip();
    const tooltip = (fixture.nativeElement as HTMLElement).querySelector('.leaflet-tooltip');

    expect(tooltip).not.toBeNull();
    expect(tooltip!.querySelector('img')).toBeNull();
    expect(tooltip!.textContent).toContain(HOSTILE);
    expect((window as unknown as { __x?: unknown }).__x).toBeUndefined();
  });
});
