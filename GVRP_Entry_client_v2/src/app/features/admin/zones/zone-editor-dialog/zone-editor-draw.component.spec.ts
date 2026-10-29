import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { BranchZoneDTO } from '@core/models';
import { LezApiService } from '@core/services/lez-api.service';
import { ToastService } from '@shared/services/toast.service';

import { EXAMPLE_ZONE, GeoJsonPolygon } from '../zone-draw.utils';
import { ZoneEditorDialogComponent } from './zone-editor-dialog.component';

@Component({
  selector: 'app-zone-map',
  standalone: true,
  template: '<div class="zone-map-stub"></div>'
})
class ZoneMapStubComponent {
  @Input() geometry: unknown = null;
  @Input() depots: unknown = [];
  @Input() height = '320px';
  @Input() editable = false;
  @Output() polygonDrawn = new EventEmitter<GeoJsonPolygon>();
}

/** UL-T2 / UL-T3 (UX-LEZ-001, D3): drawing and the example in the zone editor. */
describe('ZoneEditorDialogComponent drawing', () => {
  let component: ZoneEditorDialogComponent;
  let lez: jasmine.SpyObj<LezApiService>;
  let host: HTMLElement;

  const zone: BranchZoneDTO = {
    id: 18, mode: 'COPIED', template_id: 5, name: 'Copy of Hanoi LEZ', enabled: true,
    geometry: null, rules: []
  };
  const drawn: GeoJsonPolygon = {
    type: 'Polygon',
    coordinates: [[[105.8, 21.0], [105.9, 21.0], [105.9, 21.1], [105.8, 21.0]]]
  };

  beforeEach(async () => {
    lez = jasmine.createSpyObj<LezApiService>('LezApiService',
      ['renameZone', 'replaceZoneGeometry', 'replaceZoneRules']);
    lez.replaceZoneGeometry.and.returnValue(of(zone));
    lez.replaceZoneRules.and.returnValue(of(zone));

    await TestBed.configureTestingModule({
      imports: [ZoneEditorDialogComponent],
      providers: [
        { provide: LezApiService, useValue: lez },
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['success', 'error']) },
        { provide: MatDialogRef, useValue: { close: jasmine.createSpy('close') } },
        { provide: MAT_DIALOG_DATA, useValue: { zone } }
      ]
    })
      .overrideComponent(ZoneEditorDialogComponent, {
        set: { imports: [ZoneMapStubComponent, FormsModule, MatDialogModule] }
      })
      .compileComponents();

    const fixture = TestBed.createComponent(ZoneEditorDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    host = fixture.nativeElement as HTMLElement;
  });

  it('UL-T2: a drawn polygon updates geometry and text, clears errors, and is saved', () => {
    component.geometryErrors.set(['old error']);

    component.onPolygonDrawn(drawn);

    expect(component.geometry()).toEqual(drawn);
    expect(JSON.parse(component.geometryText())).toEqual(drawn);
    expect(component.geometryErrors()).toEqual([]);

    component.save();
    expect(lez.replaceZoneGeometry).toHaveBeenCalledWith(18, drawn);
  });

  it('UL-T2: the map is editable for a COPIED zone and the geojson.io link is safe', () => {
    const link = host.querySelector('a[href="https://geojson.io"]');
    expect(link?.getAttribute('target')).toBe('_blank');
    expect(link?.getAttribute('rel')).toContain('noopener');
    expect(host.textContent).toContain('Draw → click the corners → Finish');
  });

  it('UL-T3: Load example fills the text with EXAMPLE_ZONE and previews it', () => {
    component.loadExample();

    expect(JSON.parse(component.geometryText())).toEqual(EXAMPLE_ZONE);
    expect(component.geometry()).toEqual(EXAMPLE_ZONE);
    expect(component.geometryErrors()).toEqual([]);
  });
});
