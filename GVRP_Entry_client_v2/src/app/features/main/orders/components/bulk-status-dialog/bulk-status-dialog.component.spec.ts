import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import {
  BulkStatusDialogComponent
} from './bulk-status-dialog.component';

describe('BulkStatusDialogComponent', () => {
  let fixture: ComponentFixture<BulkStatusDialogComponent>;
  let component: BulkStatusDialogComponent;
  let dialogRef: jasmine.SpyObj<MatDialogRef<BulkStatusDialogComponent>>;

  beforeEach(async () => {
    dialogRef = jasmine.createSpyObj<MatDialogRef<BulkStatusDialogComponent>>(
      'MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      imports: [BulkStatusDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { selectedCount: 3 } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(BulkStatusDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('lists all six statuses and blocks submit without a choice', () => {
    expect(component.statuses.length).toBe(6);
    expect(component.canSubmit()).toBeFalse();
    expect(fixture.nativeElement.querySelector('button[color="primary"]').disabled).toBeTrue();
  });

  it('closes with the chosen status', () => {
    component.selectedStatus = 'SERVICING';

    expect(component.canSubmit()).toBeTrue();
    component.onSubmit();

    expect(dialogRef.close).toHaveBeenCalledWith({ status: 'SERVICING' });
  });

  it('cancels without a result', () => {
    component.onCancel();

    expect(dialogRef.close).toHaveBeenCalledWith(null);
  });
});
