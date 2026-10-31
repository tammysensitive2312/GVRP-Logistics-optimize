# Design — LEZ-FIX-001

Inputs: `request.md` (LF-R1…R4), `context.md`. Technical design by Claude, 2026-10-30. Client v2 only; no server change, no new dependency, **no new `@Input`/`@Output` on `app-zone-map`** (keeps the existing spec stubs valid, NG0303 lesson).

## 1. Drawing (`zone-map.component.ts`, `zone-draw.utils.ts`)

**D1 — No double-click zoom while drawing (LF-R1).** `startDraw()`: `this.map.doubleClickZoom.disable()`. `stopDrawing()`: `this.map.doubleClickZoom.enable()` (inside the existing `if (this.map)`). Nothing else.

**D2 — Crossing outline (LF-R2).**
- New pure export in `zone-draw.utils.ts`:
  ```ts
  /** True when two non-adjacent edges of the closed ring cross or touch (a "bow tie"). */
  export function ringSelfIntersects(ring: readonly [number, number][]): boolean
  ```
  Input: the closed GeoJSON ring of `polygonFromPoints` (first = last). Test every pair of non-adjacent edges (the first and last edge are adjacent) with a standard orientation-based segment test, including collinear overlap. O(n²) is fine (a hand-drawn outline has few points).
- `updateFinishButton()`: Finish is disabled when the polygon is `null` **or** `ringSelfIntersects(polygon.coordinates[0])`.
- The draw control gets one small text element (a `<span>`, **not** a `<button>`: the existing UL-T4 spec asserts the control's buttons are exactly Draw, Undo point, Finish, Cancel; created in `buildDrawControl`, plain DOM, hidden by default) that shows "Edges cross — undo points or cancel and redraw." while the draft crosses; `renderDraft()` updates it. Draft outline colour stays as today (optional: red dash when crossing).
- `finishDraw()`: also returns without emitting when the ring crosses (defence if the button state is stale).
- The server check stays (authority).

## 2. Presets (`zone-editor-dialog.component.{ts,html}`, `zone-rule-form.utils.ts`)

**D3 — Reset the preset select (LF-R3).** Replace the `ngModel` binding on the preset select with a plain change handler:

```html
<select [id]="'rule-preset-' + ruleIndex" (change)="onPresetSelected(ruleIndex, $event)">
  <option value="">Choose a preset…</option>
  @for (preset of rulePresets; track preset.label; let presetIndex = $index) {
    <option [value]="presetIndex">{{ preset.label }}</option>
  }
</select>
```

`onPresetSelected(index, event)`: read `select.value`; empty → nothing; otherwise `applyPreset(index, Number(value))`, then `select.value = ''`. `applyPreset` is unchanged (existing specs may call it).

**D4 — Electric preset hint (LF-R4).** `RulePreset` gets an optional `hint?: string`; "Only electric vehicles" sets `hint: 'Vehicles with no energy type recorded are banned too.'`. Under the select, one `form-hint` line: "Only electric vehicles: vehicles with no energy type recorded are banned too." rendered from the presets that have a hint (today one). Labels and `apply` functions unchanged.

## 3. Tests (new spec files only; existing specs and stubs untouched)

| ID | Case | Expected |
|---|---|---|
| LF-C1 | `ringSelfIntersects`: square; bow tie (corners clicked 1-3-2-4); triangle; concave "L" shape; ring where a vertex touches a non-adjacent edge | false; true; false; false; true |
| LF-C2 | `ZoneMapComponent` (real component, `editable = true`, in a host fixture): `startDraw()` → `map.doubleClickZoom.enabled()` false; `cancelDraw()` → true; `startDraw()` + 3 points + `finishDraw()` → true again | as stated (access the map through the component instance; if Leaflet cannot initialise in Karma, test D1 through a spy on `doubleClickZoom` and record why) |
| LF-C3 | `ZoneMapComponent`: draw a bow tie (4 points) → Finish button disabled, message visible, `finishDraw()` emits nothing; `undoPoint()` to a triangle → Finish enabled, message hidden | as stated |
| LF-C4 | Editor dialog (new spec, own stub of `app-zone-map` with the five current bindings): one rule; dispatch `change` on the preset select with value `0` | rule replaced by preset 0; select value `''` afterwards; dispatching `0` again applies again |
| LF-C5 | `RULE_PRESETS`: exactly the electric preset has a `hint`; the editor renders that hint once | as stated |

## 4. Risks

- Leaflet in Karma: the existing `zone-map-draw.component.spec.ts` (UL-T4) already creates the real component and drives the draft through its methods; follow that pattern. `map` is private — read it with bracket access in the spec only.
- D3 drops `ngModel` on that one select only; other selects keep `ngModel`.
