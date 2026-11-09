"""Convert producer formats into the evaluator's instance bundle and route set (DESIGN.md §2).

Facts about the producers, verified 2026-11-05 (sources in DESIGN.md §2.3):
- Engine callback stops (SolutionConverter.convertStops) list start depot, customers, end depot;
  `distance_to_next` / `time_to_next` come from the solve matrix in metres / seconds, 0.0 on the
  last stop and also 0.0 when a location is missing from the matrix (silent fallback).
- Entry `route_stops` stores those values unchanged; depot rows have sequence_number NULL.
- Entry `orders.service_time` is minutes (the Engine multiplies by 60).
- The Engine fixes vehicle shift start at 08:00 and end at start + max_duration (12 h if null).
Adapters only reshape data; they do not compute metrics.
"""

from __future__ import annotations

from .model import InputError, canonical_sha256

ENGINE_SHIFT_START_S = 8 * 3600
ENGINE_DEFAULT_MAX_DURATION_H = 12.0
CUSTOMER_TYPES = {"CUSTOMER", "ORDER"}
EMISSION_PROVENANCE = ("vehicle_types.vehicle_features.emission_factor, declared g/km by the system; "
                       "unit of the uniform 12.3 value is unverified (OBJECTIVE_FUNCTION.md §8)")


def _clock(seconds: float) -> str:
    s = int(round(seconds))
    return f"{s // 3600:02d}:{s % 3600 // 60:02d}:{s % 60:02d}"


def _hms(text: str | None) -> int | None:
    if text is None:
        return None
    h, m, *rest = (int(float(p)) for p in str(text).split(":"))
    return h * 3600 + m * 60 + (rest[0] if rest else 0)


def routes_from_stop_lists(stop_lists, unassigned, meta, reported) -> dict:
    """stop_lists: [(vehicle_id, [stop dicts in visit order incl. both depots])]."""
    routes = []
    for vehicle_id, stops in stop_lists:
        if len(stops) < 3 or stops[0]["type"] != "DEPOT" or stops[-1]["type"] != "DEPOT":
            raise InputError(f"vehicle {vehicle_id}: expected depot, customer stop(s), depot")
        customers = stops[1:-1]
        if any(s["type"] not in CUSTOMER_TYPES or s.get("order_id") is None for s in customers):
            raise InputError(f"vehicle {vehicle_id}: non-customer stop between depots")
        legs = []
        for s in stops[:-1]:
            if s.get("distance_to_next") is None or s.get("time_to_next") is None:
                raise InputError(f"vehicle {vehicle_id}: leg without distance/time")
            legs.append({"distance": float(s["distance_to_next"]), "time": float(s["time_to_next"])})
        start = stops[0].get("departure_time") or stops[0].get("arrival_time")
        routes.append({
            "vehicle_id": str(vehicle_id),
            "start_time": start[:8] if start else None,
            "stops": [str(s["order_id"]) for s in customers],
            "legs": legs,
        })
    return {
        "meta": meta,
        "leg_units": {"distance": "m", "time": "s"},
        "routes": routes,
        "unassigned": [str(u) for u in unassigned],
        "reported": reported,
    }


def _reported(totals: dict) -> dict:
    mapping = {"total_distance": "distance_km", "total_co2": "co2_kg", "total_cost": "total_cost_vnd",
               "served_orders": "served_orders", "unserved_orders": "unserved_orders",
               "total_vehicles_used": "vehicles_used", "total_time": "route_duration_h"}
    return {ours: float(totals[theirs]) for theirs, ours in mapping.items() if totals.get(theirs) is not None}


def from_engine_callback(payload: dict, run_id: str | None = None) -> dict:
    """Route set from an Engine completion payload (`solution_data`-shaped dict or the payload itself)."""
    data = payload.get("solution_data", payload)
    stop_lists = [(r["vehicle_id"], r["stops"]) for r in data.get("routes") or []]
    unassigned = [u["order_id"] if isinstance(u, dict) else u for u in data.get("unassigned_orders") or []]
    meta = {"producer": "engine", "producer_version": payload.get("engine_version"), "run_id": run_id,
            "seed": None, "source": "engine-callback"}
    return routes_from_stop_lists(stop_lists, unassigned, meta, _reported(data))


def from_entry_export(export: dict, carbon_price_vnd_per_t: float) -> tuple[dict, dict]:
    """Instance bundle and route set from the JSON produced by sql/export_solution.sql.

    The carbon price is an Engine source constant, not stored with the job, so the caller states it.
    """
    sol = export["solution"]
    if sol is None:
        raise InputError("export has no solution (wrong id or other branch)")
    prefs = (export.get("job_input") or {}).get("preferences") or {}
    mode = prefs.get("time_window_mode")
    if mode not in ("STRICT", "SOFT"):
        raise InputError(f"job preferences.time_window_mode is {mode!r}; set it explicitly")

    types = []
    for t in export["vehicle_types"]:
        features = t.get("vehicle_features") or {}
        max_duration = t.get("max_duration")
        types.append({
            "id": str(t["id"]),
            "capacity": {"value": t["capacity"], "unit": "kg"},
            "fixed_cost_vnd": t["fixed_cost"], "cost_per_km_vnd": t["cost_per_km"],
            "cost_per_hour_vnd": t["cost_per_hour"],
            "max_distance_km": t.get("max_distance"),
            "max_duration_h": max_duration if max_duration is not None else ENGINE_DEFAULT_MAX_DURATION_H,
            "emission_factor": {"value": features.get("emission_factor"), "unit": "g_co2_per_km",
                                "provenance": EMISSION_PROVENANCE},
            "skills": features.get("skills") or [],
        })
    type_hours = {t["id"]: t["max_duration_h"] for t in types}

    vehicles = [{
        "id": str(v["id"]), "vehicle_type_id": str(v["vehicle_type_id"]),
        "start_depot_id": str(v["start_depot_id"]), "end_depot_id": str(v["end_depot_id"]),
        "lez_skills": [],
        "shift_start": _clock(ENGINE_SHIFT_START_S),
        "shift_end": _clock(ENGINE_SHIFT_START_S + type_hours[str(v["vehicle_type_id"])] * 3600),
    } for v in export["vehicles"]]

    orders = [{
        "id": str(o["id"]), "lat": o["lat"], "lng": o["lng"],
        "demand": {"value": o["demand"], "unit": "kg"},
        "tw_start": o.get("tw_start"), "tw_end": o.get("tw_end"),
        "service_time_s": (o.get("service_time") or 0) * 60,
        "required_skills": o.get("required_skills") or [],
        "lez_skills": [],
    } for o in export["orders"]]

    bundle = {
        "meta": {
            "instance_id": f"entry-job-{sol['job_id']}",
            "source": f"gvrp_db solution {sol['id']} (branch {sol['branch_id']}), sql/export_solution.sql",
            "notes": ["LEZ skills are not exported; LEZ compliance is out of scope of this bundle",
                      "shift 08:00 + max_duration mirrors OptimizationService.createVehicle",
                      "demand/capacity unit kg as declared by the Engine model"],
        },
        "params": {
            "carbon_price_vnd_per_t": carbon_price_vnd_per_t,
            "emission_model_id": "EM0-distance-v1",
            "time_window_mode": mode,
            "default_shift_start": _clock(ENGINE_SHIFT_START_S),
        },
        "depots": [{"id": str(d["id"]), "lat": d["lat"], "lng": d["lng"]} for d in export["depots"]],
        "vehicle_types": types, "vehicles": vehicles, "orders": orders,
    }
    drift = input_drift(export)
    bundle["meta"]["input_drift"] = drift
    bundle["meta"]["sha256"] = canonical_sha256(bundle)

    stop_lists = []
    for r in sorted(export.get("routes") or [], key=lambda r: r["route_order"] or 0):
        depots = sorted((s for s in r["stops"] if s["type"] == "DEPOT"), key=lambda s: _hms(s["arrival_time"]) or 0)
        customers = sorted((s for s in r["stops"] if s["type"] != "DEPOT"), key=lambda s: s["sequence_number"])
        if len(depots) != 2:
            raise InputError(f"route {r['id']}: expected 2 depot stops, got {len(depots)}")
        stop_lists.append((r["vehicle_id"], [depots[0], *customers, depots[1]]))
    meta = {"producer": "entry-db", "producer_version": None, "run_id": f"solution-{sol['id']}",
            "seed": prefs.get("seed"), "source": f"gvrp_db solutions.id={sol['id']} type={sol.get('type')}"}
    routes = routes_from_stop_lists(stop_lists, export.get("unassigned") or [], meta, _reported(sol))
    routes["meta"]["instance_sha256"] = bundle["meta"]["sha256"]
    routes["meta"]["input_drift"] = drift
    return bundle, routes


def input_drift(export: dict) -> dict:
    """Rows of the job's inputs edited after the job ran (the bundle then differs from the solved input).

    Note: orders also change `updated_at` when the dispatch status moves (SCHEDULED, ON_ROUTE, ...),
    so an order count is a reason to look, not proof that solver inputs changed.
    """
    after = export.get("inputs_updated_after_job") or {}
    types = after.get("vehicle_types") or []
    return {"job_created_at": export.get("job_created_at"), "vehicle_types": sorted(str(t) for t in types),
            "vehicles": after.get("vehicles") or 0, "orders": after.get("orders") or 0,
            "suspect": bool(types or after.get("vehicles"))}
