import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';

import { BranchZoneDTO } from '@core/models';
import { LezApiService } from '@core/services/lez-api.service';
import { ToastService } from '@shared/services/toast.service';

import { RULE_PRESETS, emptyRuleForm } from '../zone-rule-form.utils';
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
  @Output() polygonDrawn = new EventEmitter<unknown>();
}

/** LF-C4 / LF-C5 (LEZ-FIX-001, D3/D4): the rule preset select of the zone editor. */
describe('ZoneEditorDialogComponent presets', () => {
  let fixture: ComponentFixture<ZoneEditorDialogComponent>;
  let component: ZoneEditorDialogComponent;
  let host: HTMLElement;

  const zone: BranchZoneDTO = {
    id: 18, mode: 'COPIED', template_id: 5, name: 'Copy of Hanoi LEZ', enabled: true,
    geometry: null, rules: []
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ZoneEditorDialogComponent],
      providers: [
        { provide: LezApiService, useValue: jasmine.createSpyObj('LezApiService', ['replaceZoneRules']) },
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['success', 'error']) },
        { provide: MatDialogRef, useValue: { close: jasmine.createSpy('close') } },
        { provide: MAT_DIALOG_DATA, useValue: { zone } }
      ]
    })
      .overrideComponent(ZoneEditorDialogComponent, {
        set: { imports: [ZoneMapStubComponent, FormsModule, MatDialogModule] }
      })
      .compileComponents();

    fixture = TestBed.createComponent(ZoneEditorDialogComponent);
    component = fixture.componentInstance;
    component.rules.set([{ ...emptyRuleForm(), note: 'kept', effectiveFrom: '2026-01-01' }]);
    fixture.detectChanges();
    host = fixture.nativeElement as HTMLElement;
  });

  const presetSelect = (): HTMLSelectElement => host.querySelector('#rule-preset-0') as HTMLSelectElement;
  const choose = (value: string): void => {
    presetSelect().value = value;
    presetSelect().dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };

  it('LF-C4: choosing a preset applies it and shows "Choose a preset…" again', () => {
    expect(presetSelect().value).toBe('');

    choose('0');

    expect(component.rules()[0]).toEqual(RULE_PRESETS[0].apply({ ...emptyRuleForm(), note: 'kept', effectiveFrom: '2026-01-01' }));
    expect(presetSelect().value).toBe('');
    expect(presetSelect().selectedOptions[0].textContent?.trim()).toBe('Choose a preset…');
  });

  it('LF-C4: the same preset can be applied again', () => {
    choose('0');
    component.patchRule(0, { weightGt: 7000 });
    expect(component.rules()[0].weightGt).toBe(7000);

    choose('0');

    expect(component.rules()[0].weightGt).toBe(3500);
    expect(presetSelect().value).toBe('');
  });

  it('LF-C5: only the electric preset has a hint', () => {
    expect(RULE_PRESETS.filter(preset => preset.hint).map(preset => preset.label))
      .toEqual(['Only electric vehicles']);
  });

  it('LF-C5: the editor shows the electric hint once per rule', () => {
    const hints = Array.from(host.querySelectorAll('.preset-hint')).map(p => p.textContent?.trim());

    expect(hints).toEqual(['Only electric vehicles: vehicles with no energy type recorded are banned too.']);
  });
});
