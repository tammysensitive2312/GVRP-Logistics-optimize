# Feature traceability

Feature ID: UX-LEZ-001
Inspected source revision: `fix/browser-fix-001` working tree, 2026-10-29 22:05–22:30 +07:00; implementation stacked on `feat/ux-orders-001`
Request source: `work-items/UX-LEZ-001/request.md` (user messages 2026-10-29 22:03, 22:18)

| Requirement ID | Accepted behavior and source | Design section | Code file / symbol | Test / manual case | Evidence and version | Status |
|---|---|---|---|---|---|---|
| UL-R1 | Draw the zone by clicking; samples and hints (22:03, 22:18) | D1–D3 | Planned: `zone-draw.utils`, `ZoneMapComponent.editable/polygonDrawn`, zone editor | UL-T1…T4; browser | None | NOT_VERIFIED |
| UL-R2 | Rule form help, presets, warnings (22:03) | D4–D6 | Planned: editor template, `RULE_PRESETS`, `ruleWarnings` | UL-T5, UL-T6; browser | None | NOT_VERIFIED |
| UL-R3 | Route tab scrolls (22:03) | D7 | Planned: `orders-section.component.scss` | browser | None | NOT_VERIFIED |
| UL-R4 | Expand / Collapse dialog (22:03) | D8 | Planned: `ExpandedViewDialogComponent`, `openExpanded`, `violationFocused` | UL-T8, UL-T9; browser | None | NOT_VERIFIED |
| UL-R5 | One line for repeated geometry findings | D9 | Planned: `groupGeometryMissing` | UL-T7; browser | None | NOT_VERIFIED |

## Decision log

| Decision | Source | Affected |
|---|---|---|
| Click-to-draw without a plugin | Claude 22:10, user 22:18 | UL-R1 |
| Presets and warnings non-blocking | Claude | UL-R2 |
| Fix the scroll cause as well as adding Expand | Claude 22:10, user 22:18 | UL-R3, UL-R4 |
