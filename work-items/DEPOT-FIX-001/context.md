# Context — DEPOT-FIX-001

Inspected 2026-10-30 23:30–23:45 +07:00 by Claude (cloud session) by reading files on `master` @ `081640a` through the device bridge. Nothing was run. Paths relative to `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/`.

## Path of `POST /api/v1/depots`

1. `controller/DepotController.createDepot(@RequestBody @Valid DepotInputDTO)` — branch from `CurrentUserUtil.getCurrentBranchId()` (principal), answers `201`.
2. `dto/request/DepotInputDTO` — `@NotNull` + `@DecimalMin/@DecimalMax` on `latitude` ([-90, 90]) and `longitude` ([-180, 180]); `@Getter` only (no setters), `@AllArgsConstructor(name, address, latitude, longitude)`.
3. `service/DepotService.createDepot` → `depotMapper.toEntity(dto, branch)` → `depotRepository.save`.
4. `mapper/DepotMapper.toEntity(DepotInputDTO, Branch)`: `@Mapping(target = "location", source = "dto", qualifiedByName = "createPoint")` (same on `updateEntityFromDTO`, which no service calls).
5. `mapper/GeometryMapper.createPoint(DepotInputDTO)`: returns null when a coordinate is null, otherwise calls `validateCoordinates(lat, lng)` and then builds the point (SRID 4326, x = longitude).
6. `GeometryMapper.validateCoordinates`: collects `ErrorDetails.validationWithField(AppConstant.LATITUDE / LONGITUDE)` for out-of-range values and then ends with an **unconditional** `throw new DataInvalidException(errorsInfo);`.

Because Bean Validation (step 2) already requires both coordinates, every request that reaches step 5 has them, so every valid create throws `DataInvalidException` with an **empty** detail list → `400`. VERIFIED by reading; the live `400` is NOT_VERIFIED (not run).

`GeometryMapper.createPoint(Double, Double)` (used by `OrderMapper`) does not call `validateCoordinates` and is not affected.

## Tests that exist

- `src/test/java/org/truong/gvrp_entry_api/DepotMapperTest` — `@SpringBootTest` (excluded in CI, L-2026-001); checks `toDTO` only, never `toEntity`.
- No test of `GeometryMapper` or `DepotService.createDepot` was found by file name.

## Not verified

- How the existing depots in `gvrp_db` were created (probably seed SQL); irrelevant to the fix.
- Whether any client code works around the `400` (search `depot` in `GVRP_Entry_client_v2/src` during the baseline).
