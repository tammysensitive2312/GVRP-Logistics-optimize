# SEC-TENANT-001 — cross-branch access fixes

**Problem.** A branch is a separate company. Several read paths resolved a resource by id alone, so a
user of one branch could read or modify another branch's data (SEC-002, SEC-003). The worst cases were
solutions, their revision chains, and a vehicle update that accepted a foreign fleet.

**Decision.**

- Every read or write of branch-owned data resolves the branch from the authenticated principal
  (`CurrentUserUtil.getCurrentBranchId()`), never from the request body, path or query.
- A lookup by id is also a lookup by branch: repositories expose `findByIdAndBranchId`, and a foreign
  id answers like a missing id (`404`), so existence is not disclosed.
- Derived queries inherit the same rule, e.g. `VehicleRepository.findByIdAndFleetBranchId` for a
  vehicle whose fleet carries the branch.
- Revision chains are walked only inside the owning branch, and a revision must belong to the same
  solution as its parent.
- Bulk operations and callbacks preserve the boundary; the callback resolves the branch from the job.
- `DepotRepository.findByIdAndBranchId` is safe for a null branch only because `depots.branch_id` is
  `NOT NULL` (Spring Data emits `IS NULL` for a null parameter).

**Date.** Accepted 2026-10-16 20:43 (+07:00), handoff `d1e4a1e8…`; state `done` rev 7.

**Source.** `work-items/SEC-TENANT-001/` (deleted): `design.md`, `verification.md`, `closure.md`
(requirement coverage TI-001…TI-008, clean-build and manual two-branch evidence),
`verify-output.log`.

**Still valid in this documentation.** `02-system-design/04-security-and-tenancy.md`,
`BR-001`…`BR-005`, and the tenant-isolation test cases in every branch-owned domain.

**Limitations recorded at closure.** TI-007 (null-branch behaviour) is only partially verified
(unit tests with a null branch plus static inspection). A legacy-data check on the local database found
no mixed-branch revision chains. An independent security review of the change was not performed.
