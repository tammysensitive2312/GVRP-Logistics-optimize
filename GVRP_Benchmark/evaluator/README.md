# gvrp_eval — independent route-set evaluator

Scores a route set (from the Engine, another solver, or a dispatcher) against a frozen
instance bundle with its own simulation, feasibility checks and emission model.
Design, independence rules and decisions: [DESIGN.md](DESIGN.md). Python 3.11+, standard library only.

```bash
python -m gvrp_eval evaluate --instance tests/fixtures/tiny_instance.json --routes tests/fixtures/tiny_routes.json --csv results.csv
python -m unittest discover -s tests -v
```

Exit code 2 means the input was rejected (missing unit, null emission factor, wrong leg count, ...);
no metrics are produced for rejected input.
