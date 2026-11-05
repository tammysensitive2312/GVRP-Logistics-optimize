"""Schedule simulation, feasibility checks and metrics for one route set (DESIGN.md §3-5).

Written from DESIGN.md, not from Engine sources: the point is that a solver bug cannot be
reproduced here by shared code.
"""

from __future__ import annotations

from collections import Counter

from . import __version__
from .emission import route_co2_kg
from .model import Instance, InputError, Route, RouteSet

# Violations that make a solution infeasible regardless of time-window mode.
HARD = {
    "COVERAGE_DUPLICATE", "COVERAGE_UNKNOWN", "COVERAGE_MISSING",
    "VEHICLE_REUSED", "VEHICLE_UNKNOWN", "OVER_CAPACITY",
    "SKILL_MISMATCH", "LEZ_SKILL_MISMATCH",
    "MAX_DISTANCE", "MAX_DURATION", "SHIFT_END",
}


def _violation(code, route_index, order_id, amount, unit):
    return {"code": code, "route_index": route_index, "order_id": order_id,
            "amount": amount, "unit": unit}


def _check_coverage(inst: Instance, rs: RouteSet, violations: list) -> None:
    seen = Counter()
    for r in rs.routes:
        seen.update(r.stops)
    seen.update(rs.unassigned)
    for order_id, n in seen.items():
        if order_id not in inst.orders:
            violations.append(_violation("COVERAGE_UNKNOWN", None, order_id, n, "occurrences"))
        elif n > 1:
            violations.append(_violation("COVERAGE_DUPLICATE", None, order_id, n, "occurrences"))
    for order_id in inst.orders:
        if order_id not in seen:
            violations.append(_violation("COVERAGE_MISSING", None, order_id, 0, "occurrences"))

    used = Counter(r.vehicle_id for r in rs.routes)
    for vehicle_id, n in used.items():
        if vehicle_id not in inst.vehicles:
            violations.append(_violation("VEHICLE_UNKNOWN", None, None, n, "routes"))
        elif n > 1:
            violations.append(_violation("VEHICLE_REUSED", None, None, n, "routes"))


def _route(inst: Instance, route: Route, index: int, violations: list) -> dict:
    vehicle = inst.vehicles[route.vehicle_id]
    vt = vehicle.type
    known = [o for o in route.stops if o in inst.orders]

    start = route.start_s
    if start is None:
        start = vehicle.shift_start_s if vehicle.shift_start_s is not None else inst.params.default_shift_start_s

    clock = start
    driving = waiting = service = lateness = 0.0
    stops_out = []
    for i, order_id in enumerate(route.stops):
        leg = route.legs[i]
        arrive = clock + leg.time_s
        driving += leg.time_s
        order = inst.orders.get(order_id)
        if order is None:  # already reported as COVERAGE_UNKNOWN; keep the clock moving
            clock = arrive
            continue
        wait = max(0.0, order.tw_start_s - arrive) if order.tw_start_s is not None else 0.0
        begin = arrive + wait
        late = max(0.0, begin - order.tw_end_s) if order.tw_end_s is not None else 0.0
        if late > 0:
            lateness += late
            violations.append(_violation("TIME_WINDOW", index, order_id, late, "s"))
        missing = (order.required_skills | order.lez_skills) - (vt.skills | vehicle.lez_skills)
        if missing & (order.lez_skills - order.required_skills):
            violations.append(_violation("LEZ_SKILL_MISMATCH", index, order_id, len(missing), "skills"))
        elif missing:
            violations.append(_violation("SKILL_MISMATCH", index, order_id, len(missing), "skills"))
        waiting += wait
        service += order.service_s
        clock = begin + order.service_s
        stops_out.append({"order_id": order_id, "arrival_s": arrive, "wait_s": wait,
                          "service_start_s": begin, "late_s": late, "departure_s": clock})
    clock += route.legs[-1].time_s
    driving += route.legs[-1].time_s

    distance_km = sum(leg.distance_m for leg in route.legs) / 1000.0
    duration_s = clock - start
    duration_h = duration_s / 3600.0

    units = {inst.orders[o].demand_unit for o in known}
    if units and units != {vt.capacity_unit}:
        raise InputError(f"route[{index}]: demand unit(s) {sorted(units)} differ from capacity unit '{vt.capacity_unit}'")
    load = sum(inst.orders[o].demand for o in known)
    if load > vt.capacity:
        violations.append(_violation("OVER_CAPACITY", index, None, load - vt.capacity, vt.capacity_unit))
    if vt.max_distance_km is not None and distance_km > vt.max_distance_km:
        violations.append(_violation("MAX_DISTANCE", index, None, distance_km - vt.max_distance_km, "km"))
    if vt.max_duration_h is not None and duration_h > vt.max_duration_h:
        violations.append(_violation("MAX_DURATION", index, None, (duration_h - vt.max_duration_h) * 3600.0, "s"))
    if vehicle.shift_end_s is not None and clock > vehicle.shift_end_s:
        violations.append(_violation("SHIFT_END", index, None, clock - vehicle.shift_end_s, "s"))

    co2 = route_co2_kg(inst.params.emission_model_id, distance_km, vt)
    return {
        "route_index": index, "vehicle_id": vehicle.id, "vehicle_type_id": vt.id,
        "orders": len(known), "load": load, "load_unit": vt.capacity_unit,
        "load_utilization_pct": 100.0 * load / vt.capacity if vt.capacity else None,
        "distance_km": distance_km, "start_s": start, "end_s": clock,
        "driving_s": driving, "waiting_s": waiting, "service_s": service,
        "duration_h": duration_h, "lateness_s": lateness,
        "fixed_cost_vnd": vt.fixed_cost_vnd,
        "distance_cost_vnd": distance_km * vt.cost_per_km_vnd,
        # E-D1: time cost over the whole route duration (driving + waiting + service).
        "time_cost_vnd": duration_h * vt.cost_per_hour_vnd,
        "driving_time_cost_vnd": driving / 3600.0 * vt.cost_per_hour_vnd,
        "co2_kg": co2, "emission_g_per_km": vt.emission_g_per_km,
        "stops": stops_out,
    }


def _discrepancy(metrics: dict, reported: dict) -> dict:
    out = {}
    for key, value in reported.items():
        if key in metrics and isinstance(value, (int, float)) and isinstance(metrics[key], (int, float)):
            diff = metrics[key] - value
            base = abs(metrics[key]) if metrics[key] else abs(value)
            out[key] = {"evaluator": metrics[key], "reported": value, "diff": diff,
                        "diff_pct": 100.0 * abs(diff) / base if base else 0.0}
    return out


def evaluate(inst: Instance, rs: RouteSet) -> dict:
    violations: list = []
    _check_coverage(inst, rs, violations)

    seen_vehicles = set()
    routes = []
    for i, r in enumerate(rs.routes):
        if r.vehicle_id not in inst.vehicles or r.vehicle_id in seen_vehicles:
            continue  # reported above; a second route for one vehicle is not costed twice
        seen_vehicles.add(r.vehicle_id)
        routes.append(_route(inst, r, i, violations))

    served_ids = {o for r in rs.routes for o in r.stops if o in inst.orders}
    total_demand = sum(o.demand for o in inst.orders.values())
    delivered = sum(inst.orders[o].demand for o in served_ids)
    p = inst.params
    hard = [v for v in violations if v["code"] in HARD]
    late = [v for v in violations if v["code"] == "TIME_WINDOW"]
    feasible = not hard and (p.time_window_mode == "SOFT" or not late)

    co2 = sum(r["co2_kg"] for r in routes)
    factors = {r["emission_g_per_km"] for r in routes}
    s = lambda key: sum(r[key] for r in routes)  # noqa: E731
    metrics = {
        "served_orders": len(served_ids),
        "unserved_orders": len(inst.orders) - len(served_ids),
        "delivered_demand": delivered,
        "total_demand": total_demand,
        "served_demand_pct": 100.0 * delivered / total_demand if total_demand else None,
        "feasible": feasible,
        "violations_total": len(violations),
        "violations_by_code": dict(Counter(v["code"] for v in violations)),
        "total_lateness_s": sum(v["amount"] for v in late),
        "vehicles_used": len(routes),
        "distance_km": s("distance_km"),
        "driving_h": s("driving_s") / 3600.0,
        "waiting_h": s("waiting_s") / 3600.0,
        "service_h": s("service_s") / 3600.0,
        "route_duration_h": s("duration_h"),
        "fixed_cost_vnd": s("fixed_cost_vnd"),
        "distance_cost_vnd": s("distance_cost_vnd"),
        "time_cost_vnd": s("time_cost_vnd"),
        "driving_time_cost_vnd": s("driving_time_cost_vnd"),
        "co2_kg": co2,
        "co2_cost_vnd": co2 / 1000.0 * p.carbon_price_vnd_per_t,
        # Under one emission factor for all used vehicles, EM0 CO2 is a pure multiple of distance.
        "co2_distance_collinear": len(factors) <= 1,
    }
    metrics["total_cost_vnd"] = metrics["fixed_cost_vnd"] + metrics["distance_cost_vnd"] + metrics["time_cost_vnd"]

    return {
        "evaluator_version": __version__,
        "instance_id": inst.instance_id,
        "instance_sha256": inst.sha256,
        "route_set_meta": rs.meta,
        "params": {
            "emission_model_id": p.emission_model_id,
            "time_window_mode": p.time_window_mode,
            "carbon_price_vnd_per_t": p.carbon_price_vnd_per_t,
            "emission_provenance": sorted({inst.vehicles[r["vehicle_id"]].type.emission_provenance for r in routes}),
        },
        "metrics": metrics,
        "violations": violations,
        "routes": routes,
        "discrepancy": _discrepancy(metrics, rs.reported),
    }
