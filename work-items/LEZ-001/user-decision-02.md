# User decision 02 — LEZ-001 (increment P1c)

Source: user messages in the Claude session of 2026-10-28 (+07:00, Vietnamese).

| When | User message | Recorded meaning |
|---|---|---|
| 07:43 | "tôi muốn tạo P1c luôn, tạm thời bỏ qua việc test P1b" | Start P1c now. The P1b manual test (`review-p1b.md` §5, including step 4 for the F1 fix) is **postponed, not cancelled**. P1b runtime behaviour stays NOT_VERIFIED. |
| 07:54 | Question round answer: "Form có cấu trúc (Recommended)" | **D7** — Rules of a COPIED zone are edited with a structured form (legal classes, energy types, weight, emission standard, schedule type, days, intervals, effective dates), not a raw JSON box. |
| 07:54 | Question round answer: "Cho phép chỉ phần setup (Recommended)" | **D8** — The engineer may add missing mock methods and providers to the `beforeEach` setup of exactly these existing client specs: `orders-section.component.spec.ts`, `orders-section-pareto.component.spec.ts`, `route-view.component.spec.ts`, `driver-view.component.spec.ts`, `send-route-dialog.component.spec.ts`. No assertion, `it` block, fixture value or expected value may change. Each addition must be listed under Modified Tests with the reason "D8 setup only". |

Not decided by these messages:

- Moving the run from `implement` to `verify`. P1-seed (Hanoi templates) is still open, and the P1b manual test is postponed. P1c ends with a checkpoint only.
- Any change to P1b behaviour or to findings F2–F8 of `review-p1b.md`.
