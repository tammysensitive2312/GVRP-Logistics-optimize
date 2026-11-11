# Solomon VRPTW benchmark (100 customers) — source record

| Item | Value |
|---|---|
| Content | 56 instances (C1, C2, R1, R2, RC1, RC2), 100 customers each, and one best-known solution per instance |
| Origin | M. M. Solomon (1987), "Algorithms for the vehicle routing and scheduling problems with time window constraints", Operations Research 35(2) |
| Downloaded from | CVRPLIB, PUC-Rio — `https://galgos.inf.puc-rio.br/cvrplib/index.php/en/vrptw/instances` (set "Solomon", id 22) |
| Files | `Solomon.7z` (instances) → `raw/instances/*.txt`; `Solomon-solutions.7z` → `raw/solutions/*.sol` |
| Download date | 2026-11-10 |
| Archive SHA-256 | `Solomon.7z`: `55ba67b56a47855432f1b80164db7cc87ce3c7c22bc9855f2cc0013c81d92a37` |
| | `Solomon-solutions.7z`: `88a5d385c0faf63e8eebc6a1bc755ced85d92575b816d33737b0258df459394e` |
| Per-file checksums | `SHA256SUMS` (run `sha256sum -c ../SHA256SUMS` inside `raw/`) |

Files under `raw/` are unmodified; `.gitattributes` disables line-ending conversion for them.
The first choice of source (sintef.no) was not reachable by an automated client on the download
date (bot verification), so the SINTEF best-known lists are **not** part of this dataset.

## Conventions of this copy (stated on the CVRPLIB page for the set)

- Objective: minimize total distance (not the hierarchical "vehicles first" objective).
- Distance: Euclidean, **truncated to one decimal** per leg; travel time equals that distance.
- Solution cost: total distance rounded to one decimal.
- Number of routes: at most K (25 in every instance).

Consequences:

- Costs here are not comparable with lists that use untruncated distances or that minimize the
  number of vehicles first. Example: `C101.sol` costs 827.3 under this convention; the same routes
  measure 828.94 with untruncated distances.
- A solution that is optimal for the truncated problem can violate a time window when travel
  times are not truncated (8 of the 56 solutions do, by less than 0.5 time units; see
  `docs/research/observations/EVAL-E4-solomon-anchor-2026-11-10.md`).

## Use

`GVRP_Benchmark/evaluator/tests/test_solomon_anchor.py` re-scores every published solution with
the independent evaluator (`gvrp_eval convert --from solomon --distance-convention trunc1`).
The solutions are reference points for the evaluator, not results of this project's solver.
