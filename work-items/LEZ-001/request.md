# Request — LEZ-001 (Low-emission / restricted traffic zones)

Source: user messages in the Claude session of 2026-10-24 → 2026-10-25 (Vietnamese).
Communication with the user is Vietnamese; this artifact is English (AGENTS.md §2).

## 1. Source messages and recorded decisions

| When (+07:00) | User message (Vietnamese, quoted or paraphrased) | Recorded meaning |
|---|---|---|
| 2026-10-24 16:12 | "tính năng nghiệp vụ nào tôi nên xây dựng cho hệ thống của mình tiếp theo nhỉ?" | Asked which business feature to build next. Claude proposed four options; the low-emission-zone feature was ranked first. |
| 2026-10-25 00:05 | "Vùng hạn chế phát thải, lên kế hoạch cho tính năng này trước. Tôi muốn tìm hiểu ý tưởng của tính năng này" | Chose this feature. **Planning only.** |
| 2026-10-25 12:08 | Zone ownership: "hệ thống có mẫu vùng dùng chung, và mỗi Branch bật hoặc tắt, hoặc sao chép ra để chỉnh … phải được thiết kế cẩn thận với quy tắc tách dữ liệu giữa các Branch". Mode: "Cứng tuyệt đối". `capacity` "chính là tương ứng với trọng tải của xe". | Shared system zone templates; each branch enables/disables or copies one to edit. Tenant isolation is explicitly required. Enforcement is hard. |
| 2026-10-25 21:20 | Agreed to the vehicle-selector design and to template handling (LINKED follows the latest version; COPIED is branch-owned, with a notice when the template changes). Hard vs soft "cần phải xem luật". | The decision depends on the actual regulation. Claude researched it (§5). |
| 2026-10-25 23:24 | "hiện tại scope dừng lại ở đội xe nội bộ"; switching the driver map from OSRM to the server route: "muốn làm ngay ở P1". | Scope is internal (own-account) fleets. Server-side route geometry for the driver map belongs to P1. |
| 2026-10-25 23:51 | "tôi đồng ý hết với bạn" on Claude's six clarifications (§4). Asked to use the `architecture-diagrams` skill for system design and sequence diagrams, and to design business flows: "Thiết kế thêm các luồng nghiệp vụ cho tôi đọc vì đây là chức năng lớn". | All six proposals are accepted. The deliverable of this step is the design package (documents and diagrams). |

## 2. Goal

The routing system must treat zone-based traffic restrictions (first target: Hanoi's
low-emission zone, *vùng phát thải thấp*) as **hard constraints**. A planner must not be
able to send a driver a plan that requires a restricted vehicle to stop in, or drive
through, a zone where it is not allowed at that time.

The system must also say why an order could not be planned, so that the business
sees the cost of compliance (for example, "no compliant vehicle for zone X").

## 3. Requirements

The phase column comes from the conversation's roadmap (P1 → P4). This run (LEZ-001)
covers **P1**. P2–P4 are recorded for continuity and need linked follow-up runs.

| ID | Requirement | Phase |
|---|---|---|
| LEZ-R01 | System-level **zone templates**: polygon (SRID 4326), code, name, jurisdiction, legal basis, `version`, `boundary_confidence`, source note. Branches can read templates but cannot write them. | P1 |
| LEZ-R02 | **Branch zones**: a branch enables or disables a template (`LINKED`, which always follows the latest template version) or copies it (`COPIED`, a branch-owned copy of the polygon and rules, editable). When the source template changes, a copied zone shows a notice and is never overwritten. | P1 |
| LEZ-R03 | **Rules** attached to a zone. A rule has a vehicle `selector` (legal class, energy type, weight bounds, emission standard, app-based flag), a `schedule` (`ALWAYS_FORBIDDEN`, `FORBIDDEN_DURING`, `ALLOWED_ONLY`; days of week; intervals that may cross midnight), an effective date range and a legal basis. | P1 (schema and evaluation for all types; engine enforcement of time-window types is P2) |
| LEZ-R04 | **Vehicle legal attributes** on `VehicleType`, kept separate from optimisation `capacity`: `legalClass` (explicit; a default is suggested from `VehicleCategory` and the user confirms it), `grossWeightKg`, `energyType`, `emissionStandard`. A rule that depends on a missing attribute treats the vehicle as **non-compliant**, and the UI says which attribute is missing. | P1 |
| LEZ-R05 | **Vehicle permits**: a per-vehicle exemption for a zone with an optional schedule, a validity date range and a permit number. A valid permit lifts a matching restriction. | P1 |
| LEZ-R06 | **Hard enforcement at optimisation for all-day bans**: an order inside a zone is assigned only to vehicles not banned there on the delivery date. If no compliant vehicle exists, the order is unassigned with reason `LEZ_NO_COMPLIANT_VEHICLE`, naming the zone. | P1 |
| LEZ-R07 | **Hard enforcement of time-window restrictions** (`FORBIDDEN_DURING`, `ALLOWED_ONLY`) on stops. | P2 |
| LEZ-R08 | **Transit detection**: every leg of a restricted vehicle is checked against the server route geometry (GraphHopper path). A leg that crosses a zone where the vehicle is banned at that time produces violation `ZONE_TRANSIT`. | P1 (all-day bans); P2 (time windows) |
| LEZ-R09 | **Depot inside a zone** counts as presence in that zone: at the start and end of the route and at every return to the depot. When a depot lies inside an enabled zone, configuration shows a warning listing the affected vehicles. | P1 |
| LEZ-R10 | **Manual route edits**: the existing evaluate path reports `ZONE_ACCESS` (stop) and `ZONE_TRANSIT` (leg) violations. | P1 |
| LEZ-R11 | **Send guard**: sending a route to a driver is refused while the route has any LEZ violation. | P1 |
| LEZ-R12 | **Driver map uses the server route**: the driver view shows the stored GraphHopper geometry and turn instructions instead of public OSRM, so that the checked path and the shown path are the same. | P1 |
| LEZ-R13 | **Rule snapshot per job**: the compiled restriction set (zones, versions, rules, permits used) is stored with the optimisation job, so that a past plan can be explained after the rules change. | P1 |
| LEZ-R14 | **Tenant isolation**: branch zones, copies, permits and vehicle attributes are scoped to the authenticated principal's branch (AGENTS.md §15). Cross-branch reads and writes are refused and tested. | P1 |
| LEZ-R15 | **Visibility**: a badge on orders inside an enabled zone; a compliance summary in the job result (orders in zones, unassigned for LEZ, routes with violations). | P1 |
| LEZ-R16 | **Seed data**: Hanoi templates for Đề án phase 2 (2027) and phase 3 (2028–2029), with polygons digitised approximately and `boundary_confidence` recorded. Phase 1 (ends 2026-12-31) is **not** seeded. | P1 |
| LEZ-R17 | Avoidance routing: GraphHopper custom model that detours around zones for banned vehicles. | P3 |
| LEZ-R18 | Compliance reporting, and an experiment comparing LEZ on and off (cost of compliance versus CO₂, with service parity). | P4 |

## 4. Clarifications accepted on 2026-10-25 23:51

1. `legalClass` is an explicit field (`MOTORBIKE`, `CAR`, `TRUCK`, `BUS`, `NON_MOTORIZED`). It is not inferred silently.
2. A missing legal attribute means non-compliant, with a UI warning.
3. The Đề án wording "xe tải dưới 2 tấn / 2–3,5 tấn" does not name its measure. The seed uses the **stricter reading (gross design weight)** until the original text is checked; the measure is a field of the rule template.
4. A depot inside a zone counts as presence (LEZ-R09).
5. The driver map switch includes turn instructions, in P1 (LEZ-R12).
6. Polygons are digitised manually with `boundary_confidence`; Hanoi phase 1 is not seeded.

Claude also stated two technical choices, which the user did not object to:
- Intervals that cross midnight are split.
- Presence in a zone is approximated from leg departure and arrival times.

## 5. Regulatory basis (researched 2026-10-25; Chưa xác minh against full original texts)

- **Nghị quyết 57/2025/NQ-HĐND** (Hanoi People's Council, 26/11/2025, effective 10/12/2025) is the framework. It sets phases (Vành đai 1 from 2026 → Vành đai 2 from 2028 → Vành đai 3 from 2030) and uses the wording "cấm lưu thông" (ban on circulation). Trucks with gross design weight above 3,500 kg using fossil fuel are banned. Source: hhtip.hanoi.gov.vn article "Quy định thực hiện vùng phát thải thấp trên địa bàn thành phố Hà Nội".
- **Đề án "Vùng phát thải thấp trong Vành đai 1"** (published 07/2026) holds the details. Area boundaries follow the map attached to **QĐ 3273/QĐ-UBND**. Peak hours are 06:00–09:00 and 16:00–19:30. From phase 2 (2027):
  - app-based fossil-fuel motorbikes may not circulate;
  - motorbikes below emission level 3 and cars below level 4 may not circulate;
  - trucks under 2 t only outside peak hours;
  - trucks of 2–3.5 t only 21:00–06:00, unless approved;
  - trucks above 3.5 t are banned.

  Source: xaydungchinhsach.chinhphu.vn, 2026-07-05.
- Enforcement is planned via ANPR cameras at entry points ("phạt nguội"). Specific penalties had not yet been set (lsvn.vn, 2026-05-01).
- Scope consequence: an **internal fleet** is not app-based, so its petrol motorbikes are restricted only by the emission-level rule (and by future time-window rules). In the near term the impact falls mainly on trucks.
- This is a software design input, not legal advice.

## 6. Exclusions

- Implementation of any kind. The user asked for planning and design only.
- P2–P4 (LEZ-R07 enforcement of time windows, R17, R18) in this run.
- App-based operators. The `appBased` selector is kept so templates match the law, and it evaluates to false for internal-fleet branches.
- Rules for cities other than Hanoi. The model is generic, but only Hanoi is seeded.
- An administrator UI for templates. In P1, templates change only through migrations or seed data.

## 7. Acceptance expectations (for later phases)

- An order inside an enabled zone is never assigned to a banned vehicle. It is unassigned with a reason when no compliant vehicle exists.
- A route whose stops or GraphHopper path violate an active rule cannot be sent to a driver.
- The driver sees the same path that was checked.
- Branch A cannot see or change branch B's zones, copies or permits.
- A job stays explainable after its rules change, through the stored snapshot.

## 8. Authorization boundary

- Authorized now: intake, discover and design artifacts in `work-items/LEZ-001/`, including PlantUML sources and rendered diagrams.
- **Not authorized: product code, database, tests or client changes.** These need an explicit user instruction to implement after the user reviews the design.

## 9. Open questions (do not block design)

- The measure behind "2 tấn" in the Đề án: payload or gross weight. This needs the original text.
- Machine-readable boundaries for QĐ 3273 areas. If none exist, digitise manually.
- Whether GraphHopper 11 instruction translations cover Vietnamese adequately for drivers. Chưa xác minh.
