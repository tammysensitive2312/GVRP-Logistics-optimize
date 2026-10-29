import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';

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

/** BF-C6 (BROWSER-FIX-001, C7): renaming a COPIED zone from its editor. */
describe('ZoneEditorDialogComponent rename', () => {
  let component: ZoneEditorDialogComponent;
  let lez: jasmine.SpyObj<LezApiService>;
  let calls: string[];

  const polygon = {
    type: 'Polygon',
    coordinates: [[[105.8, 21.0], [105.9, 21.0], [105.9, 21.1], [105.8, 21.0]]]
  };
  const zone: BranchZoneDTO = {
    id: 18, mode: 'COPIED', template_id: 5, name: 'Copy of Hanoi LEZ', enabled: true,
    geometry: polygon, rules: []
  };

  beforeEach(async () => {
    calls = [];
    lez = jasmine.createSpyObj<LezApiService>('LezApiService',
      ['renameZone', 'replaceZoneGeometry', 'replaceZoneRules']);
    lez.renameZone.and.callFake(() => { calls.push('rename'); return of(zone); });
    lez.replaceZoneGeometry.and.callFake(() => { calls.push('geometry'); return of(zone); });
    lez.replaceZoneRules.and.callFake(() => { calls.push('rules'); return of(zone); });

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
  });

  it('BF-C6: prefills the name and renders the input', () => {
    expect(component.name()).toBe('Copy of Hanoi LEZ');
  });

  it('BF-C6: an unchanged name sends no rename', () => {
    component.save();

    expect(lez.renameZone).not.toHaveBeenCalled();
    expect(calls).toEqual(['geometry', 'rules']);
  });

  it('BF-C6: a changed name is trimmed and sent before the geometry', () => {
    component.name.set('  Hanoi inner  ');

    component.save();

    expect(lez.renameZone).toHaveBeenCalledWith(18, 'Hanoi inner');
    expect(calls).toEqual(['rename', 'geometry', 'rules']);
  });

  it('BF-C6: a blank name is a form error and nothing is sent', () => {
    component.name.set('   ');

    component.save();

    expect(component.formErrors()).toEqual(['Name is required.']);
    expect(calls).toEqual([]);
  });

  it('BF-C6: a 201-character name is a form error and nothing is sent', () => {
    component.name.set('x'.repeat(201));

    component.save();

    expect(component.formErrors()).toEqual(['Name must be at most 200 characters.']);
    expect(calls).toEqual([]);
  });

  it('BF-C6: a rename error shows the server message and sends no geometry', () => {
    lez.renameZone.and.returnValue(throwError(() => ({
      message: 'Invalid data validation.',
      status: 400,
      originalError: { error: { errors: [{ message: 'Another zone of this branch is already named Hanoi' }] } }
    })));
    component.name.set('Hanoi');

    component.save();

    expect(component.formErrors()).toEqual(['Another zone of this branch is already named Hanoi']);
    expect(lez.replaceZoneGeometry).not.toHaveBeenCalled();
    expect(component.busy()).toBeFalse();
  });
});
