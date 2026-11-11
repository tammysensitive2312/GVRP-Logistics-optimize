# gvrp_eval — independent route-set evaluator

Scores a route set (from the Engine, another solver, or a dispatcher) against a frozen
instance bundle with its own simulation, feasibility checks and emission model.
Design, independence rules and decisions: [DESIGN.md](DESIGN.md). Python 3.11+, standard library only.

```bash
python -m gvrp_eval evaluate --instance tests/fixtures/tiny_instance.json --routes tests/fixtures/tiny_routes.json --csv results.csv
python -m unittest discover -s tests -v
```

Stored Entry solution (read-only, needs Docker and `GVRP_DB_PASSWORD`):

```bash
sh sql/export_solution.sh 72 > export.json
python -m gvrp_eval convert --from entry-export export.json --carbon-price 150000 --instance-out bundle.json --routes-out routes.json
python -m gvrp_eval evaluate --instance bundle.json --routes routes.json
```

Compare goals or algorithms on one instance (one route set per seed per group):

```bash
python -m gvrp_eval compare --instance bundle.json --a cost_s*.json --b eco_s*.json --label-a COST --label-b ECO --out compare.json
```

VRPTW benchmark instance (Solomon / Gehring-Homberger text format) with a published solution:

```bash
python -m gvrp_eval convert --from solomon ../datasets/solomon/raw/instances/C101.txt --solution ../datasets/solomon/raw/solutions/C101.sol --distance-convention trunc1 --instance-out bundle.json --routes-out routes.json
python -m gvrp_eval evaluate --instance bundle.json --routes routes.json
```

`distance_km` × 1000 is the benchmark distance (1 unit = 1 m); cost and CO2 are 0 by declaration.
`--distance-convention` must match the published solution (`trunc1` for the CVRPLIB copy in
`../datasets/solomon`); `tests/test_solomon_anchor.py` re-scores all 56 published solutions.

Exports contain customer coordinates: keep them out of the repository.

Exit code 2 means the input was rejected (missing unit, null emission factor, wrong leg count, ...);
no metrics are produced for rejected input.
