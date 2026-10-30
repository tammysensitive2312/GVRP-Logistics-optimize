# Design — DEPOT-FIX-001

Inputs: `request.md` (DF-R1, DF-R2), `context.md`. Technical design by Claude, 2026-10-30. Entry API only; no schema change, no new dependency, no client change.

## 1. Change

**S1 — `GeometryMapper.validateCoordinates` (DF-R1, DF-R2).** Throw only when at least one detail was collected:

```java
if (!errorsInfo.isEmpty()) {
    throw new DataInvalidException(errorsInfo);
}
```

Nothing else in `GeometryMapper`, `DepotMapper`, `DepotService` or `DepotController` changes. Tenant scoping is unchanged (branch from the principal).

## 2. Tests (new files only; no DB, no Spring context — L-2026-001)

| ID | Case | Expected |
|---|---|---|
| DF-T1 | `new GeometryMapper().createPoint(new DepotInputDTO("Depot A", "1 Trang Tien, Ha Noi", 21.02, 105.85))` | Point with `getY() == 21.02`, `getX() == 105.85`, SRID 4326; no exception |
| DF-T2 | same with latitude `95.0` | `DataInvalidException` whose details contain exactly one entry with field `latitude` |
| DF-T3 | same with longitude `-181.0`; and with both out of range | one detail `longitude`; two details `latitude`, `longitude` |
| DF-T4 | `DepotService.createDepot` with mocked `BranchRepository` / `DepotRepository` and the **generated** `DepotMapperImpl` wired with a real `GeometryMapper` (set its field with `ReflectionTestUtils`; read the field name in `target/generated-sources/annotations`) | `depotRepository.save` receives a `Depot` whose `location` is the point and whose `branch` is the caller's branch. If the generated mapper cannot be built without Spring, record why and rely on DF-T1…T3 plus the live check. |

Suggested class names: `mapper/GeometryMapperTest`, `service/DepotServiceCreateTest` (under `src/test/java/org/truong/gvrp_entry_api/`).

## 3. Live check (optional, recorded)

With the user's Entry API running: create one depot from the client (depot page or the setup wizard's first step) or `POST /api/v1/depots` with a valid body → `201`, depot listed. Record the depot name created (test row in the shared database).

## 4. Risks

- Low. The only behaviour change is that valid coordinates stop failing. Out-of-range values were already refused earlier by Bean Validation (`@DecimalMin/@DecimalMax`), so `validateCoordinates` is a second guard only.
