import {
  Directive,
  ElementRef,
  HostListener,
  inject,
  input,
  OnDestroy,
  output,
  PLATFORM_ID,
  Renderer2
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

/**
 * Resizable divider
 *
 * Ports V1 `components/UI Components/resizable-divider.js`. Drag the divider to
 * resize the element passed via `appResizableTarget`; the height is constrained
 * to `minRatio`/`maxRatio` of the container.
 *
 * Improvements over both V1 and the inline version this replaces:
 * - Pointer events, so mouse and touch share one code path (V1 duplicated the
 *   logic for touchstart/touchmove/touchend).
 * - Global listeners are attached only while dragging instead of living on
 *   `document` for the lifetime of the page.
 * - No `document.querySelector`: the target element is passed in.
 * - Browser-guarded for SSR, and `resized` fires so the host can re-measure
 *   things like a Leaflet map.
 */
@Directive({
  selector: '[appResizableDivider]',
  standalone: true,
  host: {
    '[class.dragging]': 'dragging',
    '[attr.role]': '"separator"',
    '[attr.aria-orientation]': '"horizontal"'
  }
})
export class ResizableDividerDirective implements OnDestroy {
  /** Element whose height is changed while dragging. */
  readonly target = input.required<HTMLElement | undefined>({
    alias: 'appResizableTarget'
  });
  /** Container the ratios are measured against; defaults to the target's parent. */
  readonly container = input<HTMLElement | undefined>(undefined, {
    alias: 'appResizableContainer'
  });
  readonly minRatio = input(0.05, { alias: 'appResizableMinRatio' });
  readonly maxRatio = input(0.95, { alias: 'appResizableMaxRatio' });

  /** Emits the new height in pixels during and after the drag. */
  readonly resized = output<number>();
  readonly resizeEnd = output<number>();

  protected dragging = false;

  private readonly renderer = inject(Renderer2);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  private startY = 0;
  private startHeight = 0;
  private cleanupFns: (() => void)[] = [];

  @HostListener('pointerdown', ['$event'])
  onPointerDown(event: PointerEvent): void {
    if (!this.isBrowser) return;

    const target = this.target();
    if (!target) return;

    this.dragging = true;
    this.startY = event.clientY;
    this.startHeight = target.offsetHeight;

    this.host.nativeElement.setPointerCapture?.(event.pointerId);
    this.renderer.setStyle(document.body, 'cursor', 'row-resize');
    this.renderer.setStyle(document.body, 'user-select', 'none');

    this.cleanupFns.push(
      this.renderer.listen('document', 'pointermove', (moveEvent: PointerEvent) =>
        this.onPointerMove(moveEvent)
      ),
      this.renderer.listen('document', 'pointerup', () => this.stopDragging()),
      this.renderer.listen('document', 'pointercancel', () => this.stopDragging())
    );

    event.preventDefault();
  }

  ngOnDestroy(): void {
    this.releaseListeners();
    this.restoreBodyStyles();
  }

  private onPointerMove(event: PointerEvent): void {
    if (!this.dragging) return;

    const target = this.target();
    if (!target) return;

    const container = this.container() ?? target.parentElement;
    if (!container) return;

    const containerHeight = container.offsetHeight;
    const minHeight = containerHeight * this.minRatio();
    const maxHeight = containerHeight * this.maxRatio();

    const nextHeight = this.startHeight + (event.clientY - this.startY);
    if (nextHeight < minHeight || nextHeight > maxHeight) return;

    this.renderer.setStyle(target, 'height', `${nextHeight}px`);
    this.resized.emit(nextHeight);
  }

  private stopDragging(): void {
    if (!this.dragging) return;

    this.dragging = false;
    this.releaseListeners();
    this.restoreBodyStyles();

    const height = this.target()?.offsetHeight ?? 0;
    this.resizeEnd.emit(height);
  }

  private releaseListeners(): void {
    this.cleanupFns.forEach(cleanup => cleanup());
    this.cleanupFns = [];
  }

  private restoreBodyStyles(): void {
    if (!this.isBrowser) return;
    this.renderer.removeStyle(document.body, 'cursor');
    this.renderer.removeStyle(document.body, 'user-select');
  }
}
