import { inject, Injectable } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { map, Observable } from 'rxjs';

import {
  ConfirmDialogComponent,
  ConfirmDialogData
} from '@shared/components/confirm-dialog/confirm-dialog.component';

@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private readonly dialog = inject(MatDialog);

  /** Emits true only when the user confirms; backdrop / Esc resolve to false. */
  ask(data: ConfirmDialogData): Observable<boolean> {
    return this.dialog
      .open<ConfirmDialogComponent, ConfirmDialogData, boolean>(ConfirmDialogComponent, {
        data,
        width: '420px',
        autoFocus: 'dialog',
        restoreFocus: true
      })
      .afterClosed()
      .pipe(map(result => result === true));
  }
}
