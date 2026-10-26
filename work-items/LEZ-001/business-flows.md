# Business flows — LEZ-001 (Low-emission zones)

Status: PROPOSED. Written for the product owner to read. Technical detail is in `design.md`.
Process view: `diagrams/lez-activity-daily-planning.puml`.

## Actors

| Actor | Who | Does |
|---|---|---|
| **Planner** | Branch user with role `PLANNER` | Sets up zones, vehicle legal data and permits; plans, edits and sends routes |
| **Driver** | Branch user with role `DRIVER` | Follows the route he was sent |
| **System maintainer** | Repository owner (no in-app role yet) | Publishes and updates zone templates through migrations or seed data, when the law changes |
| **System** | Entry API + Engine API | Compiles rules, optimises, certifies compliance, blocks non-compliant sends |

## Business rules (summary)

- **BR-1 Hard enforcement.** No route that violates an active zone rule can be sent to a driver.
- **BR-2 Presence = stop or pass-through.** A vehicle is "in a zone" when it stops there, when its depot is there, or when its path crosses it.
- **BR-3 Rules apply by date.** A rule applies only on delivery dates inside its effective range. Future phases can be loaded early.
- **BR-4 Unknown means not allowed.** If a rule needs a vehicle fact that is missing (gross weight, energy type, emission level, legal class), the vehicle is treated as restricted until the fact is entered.
- **BR-5 Permits are explicit exceptions.** Only a valid permit for that vehicle, zone and time lifts a restriction.
- **BR-6 Templates are shared; copies are owned.** A linked zone always follows the latest template. A copied zone belongs to the branch and is never changed by the system; the branch is only notified.
- **BR-7 One company's data stays in that company.** Zones, copies, permits and vehicle data are visible only to their branch.
- **BR-8 Explainability.** Every plan keeps the rules it was planned with.

---

## BF-01 — Turn on the legal zones for a branch (one-time setup)

**Trigger:** the branch starts operating in a city that has zones, or a new template is published.

1. The planner opens **Zones** and sees the published templates (for example "Hà Nội – Khu vực 1–3, 2027", "Hà Nội – Vành đai 1, 2028") with legal basis and boundary confidence.
2. For each relevant template the planner chooses:
   - **Enable**: follow the official template as it is. *Recommended for legal zones.*
   - **Copy to edit**: make a branch-owned version, for example to add a stricter internal zone around a hospital. The copy can be edited.
3. The system shows a warning if a **depot lies inside** an enabled zone, and lists the vehicles that could not leave it on restricted days (BR-2).
4. The orders list now shows an **LEZ badge** on orders inside enabled zones.

**Outcome:** the branch's planning is subject to the zones from now on.
**Exceptions:** the planner disables a zone: planning stops considering it, and earlier plans that used it are marked to be re-checked.

## BF-02 — Record the legal facts of the fleet

**Trigger:** BF-01 done, a new vehicle type is created, or the system warns about missing data.

1. In each **vehicle type**, the planner fills in *legal class* (motorbike, car, truck, bus, non-motorised; the system suggests one from the category), *gross weight*, *energy type* and *emission level*.
2. Until these are complete, the system treats the type as restricted in every zone whose rules need them (BR-4) and shows which fact is missing.

**Outcome:** the system can decide, for each vehicle, where and when it may go.
**Note:** *capacity* stays the loading limit used for optimisation. *Gross weight* is the legal figure; the two are different on purpose.

## BF-03 — Register a permit

**Trigger:** a vehicle has an authorisation (for example a truck allowed into the centre at night).

1. The planner opens the vehicle → **Permits** → adds the zone, the permit number, the validity dates and optionally the allowed hours.
2. From then on, the vehicle is allowed in that zone within the permit's dates and hours (BR-5).

**Exceptions:** an expired permit stops counting automatically. Routes planned with it are blocked at send time (BF-06).

## BF-04 — Daily planning with zones (main flow)

**Trigger:** orders for a delivery date are ready.

1. The planner imports or creates the orders. In-zone orders carry the LEZ badge.
2. The planner starts an optimisation for **one delivery date**.
3. The system **compiles** the rules effective on that date for the branch's enabled zones, the fleet's legal facts and the permits into "vehicle X may not be in zone Z (all day / during hours H)", and **stores this snapshot** with the job (BR-3, BR-8).
4. The engine plans routes:
   - in-zone orders go only to vehicles allowed there;
   - vehicles that cannot even leave a depot inside a zone are left out.
5. The system **certifies** every route against the snapshot, including the actual road path of every leg.
6. The planner reviews the **compliance panel**:
   - orders unassigned because **no compliant vehicle** exists, per zone;
   - routes with a **pass-through** violation (P1 detects these but cannot yet route around zones);
   - routes with a **time-of-day** violation, for example a small truck reaching the zone in the morning peak. In P1 the optimiser does not yet avoid these; the certification catches them and they block sending.
7. The planner resolves the issues. Options:
   - **add or assign an allowed vehicle** (e.g. an electric one);
   - **register a permit** (BF-03);
   - **move the order** to another date or time window;
   - **edit the route** (BF-05);
   - **accept that the order stays unassigned** and inform the customer.

**Outcome:** a plan whose routes are all *Compliant*, ready to send.
**Business value:** the panel shows the cost of compliance, i.e. how many orders and which zones need clean vehicles or permits.

## BF-05 — Edit a route by hand

1. The planner changes the stop order, or moves orders between vehicles.
2. On preview, the system shows any violation: *stop in a banned zone*, *path crosses a banned zone*, and the existing ones (capacity, time window, skill, distance).
3. The planner can **save** a revision even with violations, for discussion. A violating revision **cannot be sent** (BR-1).

## BF-06 — Send routes to drivers

1. The planner sends a route to a driver.
2. The system **re-checks the route with today's data**: current templates, copies, permits and vehicle facts.
   - **Compliant** → sent. The driver gets the route.
   - **Violation or missing data** → refused, with the list of reasons. Typical cases: a permit expired, a rule or template changed, a vehicle fact was deleted.
3. If refused, the planner fixes the plan (BF-04 step 7 or BF-05) and sends again.

## BF-07 — Driver follows the route

1. The driver opens the route.
2. The map shows **exactly the path the system checked**, with turn-by-turn instructions from the same router. Public OSRM is no longer used for these routes.
3. Stops inside a zone are marked so the driver knows the area is controlled.

**Why it matters:** if the screen showed a different path, a driver could enter a banned zone while following it. Camera enforcement at zone entries would then fine the company.

## BF-08 — The law changes

**Trigger:** the authority publishes a new phase, new boundaries or new hours.

1. The system maintainer publishes a **new template version** (or a new template), with legal basis and boundary confidence.
2. **Linked** zones take the new version immediately. Plans that used the zone become *Stale* and are re-checked before sending (BF-06).
3. **Copied** zones are **not changed**. The planner sees "template updated", compares the versions, and either acknowledges (keeps the copy) or makes a new copy.
4. Rules with a future start date need no action. They begin to apply on their date (BR-3).

## BF-09 — Explain an old plan

1. Weeks later, someone asks why an order was not delivered by truck.
2. The job's **snapshot** shows the zones, template versions, rules and permits that were active when the plan was made (BR-8), even if they have changed since.

---

## What is out of scope in P1 (later phases)

| Need | Phase |
|---|---|
| Optimiser **avoids** time-of-day conflicts (trucks only at night, small trucks outside peak hours). In P1 these conflicts are already detected and block sending. | P2 |
| Optimiser routes **around** zones instead of only detecting pass-through | P3 |
| Compliance reports; experiment on the cost of compliance versus CO₂ saved | P4 |
| App-based (platform) operators | Out of scope: internal fleets only |
| In-app editing of official templates | Later, needs an administrator role |
