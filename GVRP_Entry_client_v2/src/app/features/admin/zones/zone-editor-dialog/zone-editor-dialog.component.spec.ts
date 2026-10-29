import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { BranchZoneDTO } from '@core/models';
import { LezApiService } from '@core/services/lez-api.service';
import { ToastService } from '@shared/services/toast.service';

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
}

/** ZP-T2: geometry of the COPIED editor and what reaches the server. */
describe('ZoneEditorDialogComponent', () => {
  let fixture: ComponentFixture<ZoneEditorDialogComponent>;
  let component: ZoneEditorDialogComponent;
  let lez: jasmine.SpyObj<LezApiService>;
  let close: jasmine.Spy;

  const zone: BranchZoneDTO = {
    id: 18,
    mode: 'COPIED',
    template_id: 5,
    name: 'Copy of Hanoi LEZ',
    enabled: true,
    geometry: null,
    rules: []
  };

  const polygon = {
    type: 'Polygon',
    coordinates: [[[105.8, 21.0], [105.9, 21.0], [105.9, 21.1], [105.8, 21.0]]]
  };

  const setup = async (data: BranchZoneDTO = zone): Promise<void> => {
    lez = jasmine.createSpyObj<LezApiService>('LezApiService', [
      'replaceZoneGeometry', 'replaceZoneRules'
    ]);
    lez.replaceZoneGeometry.and.returnValue(of(data));
    lez.replaceZoneRules.and.returnValue(of(data));
    close = jasmine.createSpy('close');

    await TestBed.configureTestingModule({
      imports: [ZoneEditorDialogComponent],
      providers: [
        { provide: LezApiService, useValue: lez },
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['success', 'error']) },
        { provide: MatDialogRef, useValue: { close } },
        { provide: MAT_DIALOG_DATA, useValue: { zone: data } }
      ]
    })
      .overrideComponent(ZoneEditorDialogComponent, {
        set: { imports: [ZoneMapStubComponent, FormsModule, MatDialogModule] }
      })
      .compileComponents();

    fixture = TestBed.createComponent(ZoneEditorDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  it('rejects text that is not JSON and blocks the save', async () => {
    await setup();

    component.geometryText.set('not json at all');
    component.applyPastedGeometry();
    component.save();

    expect(component.geometryErrors()).toContain('The text is not valid JSON.');
    expect(component.geometry()).toBeNull();
    expect(lez.replaceZoneGeometry).not.toHaveBeenCalled();
    expect(component.formErrors().length).toBeGreaterThan(0);
  });

  it('rejects a Point: it is not a zone', async () => {
    await setup();

    component.geometryText.set(JSON.stringify({ type: 'Point', coordinates: [105.8, 21.0] }));
    component.applyPastedGeometry();
    component.save();

    expect(component.geometryErrors().join(' ')).toContain('Polygon or MultiPolygon');
    expect(component.geometry()).toBeNull();
    expect(lez.replaceZoneGeometry).not.toHaveBeenCalled();
  });

  it('accepts a Polygon and sends the parsed object and the rule list', async () => {
    await setup();

    component.geometryText.set(JSON.stringify(polygon));
    component.applyPastedGeometry();

    expect(component.geometry()).toEqual(polygon);

    component.save();

    expect(lez.replaceZoneGeometry).toHaveBeenCalledWith(18, polygon);
    expect(lez.replaceZoneRules).toHaveBeenCalledWith(18, []);
    expect(close).toHaveBeenCalledWith(true);
  });

  it('accepts a Feature that wraps a MultiPolygon', async () => {
    await setup();
    const feature = {
      type: 'Feature',
      properties: {},
      geometry: {
        type: 'MultiPolygon',
        coordinates: [polygon.coordinates]
      }
    };

    component.geometryText.set(JSON.stringify(feature));
    component.applyPastedGeometry();

    expect(component.geometry()).toEqual(feature.geometry);
  });

  it('does not save a LINKED zone', async () => {
    await setup({ ...zone, mode: 'LINKED' });

    component.geometryText.set(JSON.stringify(polygon));
    component.applyPastedGeometry();
    component.save();

    expect(component.isCopied()).toBeFalse();
    expect(lez.replaceZoneGeometry).not.toHaveBeenCalled();
  });
});
