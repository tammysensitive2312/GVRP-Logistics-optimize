"""Solomon / Gehring-Homberger VRPTW benchmark adapter (DESIGN.md §9.3, phase E4).

Purpose: anchor the evaluator against published solutions. If the evaluator reproduces the
published distance and finds a published solution feasible, its simulation and checks agree with
an external reference that shares no code with this repository.

Benchmark conventions and how they map onto the evaluator's units:
- Coordinates are planar; travel distance is the Euclidean distance and travel time equals
  distance (speed 1). Mapping: 1 benchmark distance unit = 1 m, 1 time unit = 1 s, so the
  evaluator's `distance_km` is the benchmark distance / 1000. `benchmark_distance()` undoes this.
- Two distance conventions exist and the caller picks the one of the reference being checked:
  `exact` (double precision) and `trunc1` (each leg truncated to one decimal, travel time equal
  to the truncated distance; CVRPLIB's convention for the Solomon set, whose solution cost is
  the sum rounded to one decimal). A route set records its convention and declares the
  truncation, so the leg sanity bound is relaxed by exactly 0.1.
- Customer 0 is the depot. Its DUE DATE is the latest return time (vehicle shift end).
- Service must start within [READY TIME, DUE DATE]; early arrival waits. Same rule as DESIGN.md §3.
- The benchmark has no costs and no emissions: costs are 0 and the emission factor is 0 with an
  explicit provenance, so cost and CO2 outputs are 0 and must not be interpreted.
"""

from __future__ import annotations

import math
import re

from .model import InputError, canonical_sha256

NO_EMISSION = "not applicable: VRPTW benchmark instance has no emission data"


def _clock(units: float, where: str) -> str:
    if units != int(units) or units < 0:
        raise InputError(f"{where}: time {units} is not a non-negative integer")
    s = int(units)
    return f"{s // 3600:02d}:{s % 3600 // 60:02d}:{s % 60:02d}"


def parse_instance_text(text: str) -> dict:
    """Parse the classic text format: name, VEHICLE block (number, capacity), CUSTOMER table."""
    lines = [ln.strip() for ln in text.splitlines() if ln.strip()]
    if not lines:
        raise InputError("empty benchmark file")
    name = lines[0]
    fleet = None
    customers = []
    for i, line in enumerate(lines):
        if line.upper().startswith("NUMBER") and i + 1 < len(lines):
            parts = lines[i + 1].split()
            if len(parts) != 2:
                raise InputError("VEHICLE block: expected '<number> <capacity>'")
            fleet = (int(parts[0]), float(parts[1]))
        fields = line.split()
        if len(fields) == 7:
            try:
                row = [float(f) for f in fields]
            except ValueError:
                continue
            customers.append({"id": int(row[0]), "x": row[1], "y": row[2], "demand": row[3],
                              "ready": row[4], "due": row[5], "service": row[6]})
    if fleet is None:
        raise InputError("VEHICLE block not found")
    if not customers or customers[0]["id"] != 0:
        raise InputError("CUSTOMER table must start with the depot (customer 0)")
    ids = [c["id"] for c in customers]
    if len(set(ids)) != len(ids):
        raise InputError("duplicate customer number")
    return {"name": name, "vehicles": fleet[0], "capacity": fleet[1],
            "depot": customers[0], "customers": customers[1:]}


def to_bundle(parsed: dict) -> dict:
    depot = parsed["depot"]
    name = parsed["name"]
    bundle = {
        "meta": {"instance_id": f"solomon-{name}", "source": f"VRPTW benchmark instance {name}",
                 "notes": ["1 distance unit = 1 m, 1 time unit = 1 s; planar Euclidean, untruncated",
                           "costs and emissions are not part of the benchmark (all 0)"]},
        "params": {"carbon_price_vnd_per_t": 0, "emission_model_id": "EM0-distance-v1",
                   "time_window_mode": "STRICT", "default_shift_start": _clock(depot["ready"], "depot"),
                   "coordinates": "planar"},
        "depots": [{"id": "0", "lat": depot["y"], "lng": depot["x"]}],
        "vehicle_types": [{
            "id": "K", "capacity": {"value": parsed["capacity"], "unit": "unit"},
            "fixed_cost_vnd": 0, "cost_per_km_vnd": 0, "cost_per_hour_vnd": 0,
            "max_distance_km": None, "max_duration_h": None,
            "emission_factor": {"value": 0, "unit": "g_co2_per_km", "provenance": NO_EMISSION},
            "skills": [],
        }],
        "vehicles": [{"id": f"K{i}", "vehicle_type_id": "K", "start_depot_id": "0", "end_depot_id": "0",
                      "lez_skills": [], "shift_start": _clock(depot["ready"], "depot"),
                      "shift_end": _clock(depot["due"], "depot")}
                     for i in range(1, parsed["vehicles"] + 1)],
        "orders": [{"id": str(c["id"]), "lat": c["y"], "lng": c["x"],
                    "demand": {"value": c["demand"], "unit": "unit"},
                    "tw_start": _clock(c["ready"], f"customer {c['id']}"),
                    "tw_end": _clock(c["due"], f"customer {c['id']}"),
                    "service_time_s": c["service"], "required_skills": [], "lez_skills": []}
                   for c in parsed["customers"]],
    }
    bundle["meta"]["sha256"] = canonical_sha256(bundle)
    return bundle


ROUTE_LINE = re.compile(r"^\s*Route\s*#?\s*\d+\s*:\s*(.*)$", re.IGNORECASE)
COST_LINE = re.compile(r"^\s*(?:Cost|Distance)\s*:?\s*([0-9]+(?:\.[0-9]+)?)\s*$", re.IGNORECASE)


def parse_solution_text(text: str) -> dict:
    """Parse 'Route k : c1 c2 ...' lines (SINTEF / CVRPLIB style); depot zeros are dropped."""
    routes, cost = [], None
    for line in text.splitlines():
        m = ROUTE_LINE.match(line)
        if m:
            try:
                stops = [int(tok) for tok in m.group(1).replace("-", " ").replace(",", " ").split()]
            except ValueError:
                raise InputError(f"unreadable route line: {line.strip()!r}") from None
            stops = [s for s in stops if s != 0]
            if stops:
                routes.append(stops)
            continue
        m = COST_LINE.match(line)
        if m:
            cost = float(m.group(1))
    if not routes:
        raise InputError("no 'Route k : ...' lines found")
    return {"routes": routes, "reported_distance": cost}


CONVENTIONS = ("exact", "trunc1")


def leg_distance(a: tuple[float, float], b: tuple[float, float], convention: str) -> float:
    """Euclidean distance; `trunc1` truncates it to one decimal (CVRPLIB convention for Solomon)."""
    d = math.hypot(b[0] - a[0], b[1] - a[1])
    return math.floor(d * 10.0) / 10.0 if convention == "trunc1" else d


def to_route_set(parsed: dict, solution: dict, bundle: dict, run_id: str | None = None,
                 producer: str = "published-solution", convention: str = "exact") -> dict:
    if convention not in CONVENTIONS:
        raise InputError(f"distance convention must be one of {CONVENTIONS}")
    if len(solution["routes"]) > parsed["vehicles"]:
        raise InputError(f"solution has {len(solution['routes'])} routes but the instance has "
                         f"{parsed['vehicles']} vehicles")
    xy = {c["id"]: (c["x"], c["y"]) for c in parsed["customers"]}
    depot = (parsed["depot"]["x"], parsed["depot"]["y"])
    routes = []
    for k, stops in enumerate(solution["routes"], start=1):
        unknown = [s for s in stops if s not in xy]
        if unknown:
            raise InputError(f"route {k}: unknown customer(s) {unknown}")
        points = [depot, *(xy[s] for s in stops), depot]
        legs = []
        for a, b in zip(points, points[1:]):
            d = leg_distance(a, b, convention)
            legs.append({"distance": d, "time": d})
        routes.append({"vehicle_id": f"K{k}", "start_time": None,
                       "stops": [str(s) for s in stops], "legs": legs})
    served = {s for stops in solution["routes"] for s in stops}
    reported = {}
    if solution.get("reported_distance") is not None:
        reported["distance_km"] = solution["reported_distance"] / 1000.0
    return {
        "meta": {"producer": producer, "producer_version": None, "run_id": run_id, "seed": None,
                 "instance_sha256": bundle["meta"]["sha256"], "distance_convention": convention},
        "leg_units": {"distance": "m", "time": "s",
                      "distance_truncation": 0.1 if convention == "trunc1" else 0.0},
        "routes": routes,
        "unassigned": [str(c["id"]) for c in parsed["customers"] if c["id"] not in served],
        "reported": reported,
    }


def benchmark_distance(evaluation: dict) -> float:
    """Total distance in benchmark units from an evaluation of a Solomon bundle."""
    return evaluation["metrics"]["distance_km"] * 1000.0
