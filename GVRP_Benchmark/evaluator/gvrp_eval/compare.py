"""Compare two groups of evaluated route sets on one instance (DESIGN.md §7, E-D4).

A difference in emissions or cost is reported as a finding only when both groups serve the same
demand and every run is feasible (service parity). Otherwise the verdict is
SERVICE_PARITY_FAILED and the deltas are shown for diagnosis only.

Parity is checked across all runs of both groups, not on group means: the largest and smallest
served-order counts may differ by at most `tol_orders`, and delivered demand by at most
`tol_demand_pct` percent of the instance's total demand.
"""

from __future__ import annotations

import math
from statistics import mean, stdev

MIN_SEEDS = 5
METRICS = (
    "served_orders", "delivered_demand", "vehicles_used", "distance_km", "route_duration_h",
    "waiting_h", "co2_kg", "total_cost_vnd", "fixed_cost_vnd", "distance_cost_vnd", "time_cost_vnd",
)


def _summary(values: list[float]) -> dict:
    return {"n": len(values), "mean": mean(values), "sd": stdev(values) if len(values) > 1 else None,
            "min": min(values), "max": max(values)}


def _welch(a: list[float], b: list[float]) -> dict | None:
    """Welch's t statistic and degrees of freedom; None when undefined. Not a p-value."""
    if len(a) < 2 or len(b) < 2:
        return None
    va, vb = stdev(a) ** 2 / len(a), stdev(b) ** 2 / len(b)
    if va + vb == 0:
        return None
    t = (mean(b) - mean(a)) / math.sqrt(va + vb)
    df = (va + vb) ** 2 / ((va ** 2 / (len(a) - 1) if len(a) > 1 else 0) + (vb ** 2 / (len(b) - 1) if len(b) > 1 else 0))
    return {"t": t, "df": df}


def compare(results_a: list[dict], results_b: list[dict], *, tol_orders: int = 0,
            tol_demand_pct: float = 0.5, label_a: str = "A", label_b: str = "B") -> dict:
    if not results_a or not results_b:
        raise ValueError("each group needs at least one evaluated route set")
    hashes = {r["instance_sha256"] for r in results_a + results_b}
    if len(hashes) != 1:
        raise ValueError(f"route sets were evaluated on different instances: {sorted(hashes)}")
    models = {r["params"]["emission_model_id"] for r in results_a + results_b}
    if len(models) != 1:
        raise ValueError(f"route sets use different emission models: {sorted(models)}")

    runs = results_a + results_b
    total_demand = runs[0]["metrics"]["total_demand"]
    served = [r["metrics"]["served_orders"] for r in runs]
    delivered = [r["metrics"]["delivered_demand"] for r in runs]
    infeasible = [r["route_set_meta"].get("run_id") for r in runs if not r["metrics"]["feasible"]]
    demand_gap_pct = 100.0 * (max(delivered) - min(delivered)) / total_demand if total_demand else 0.0
    parity = {
        "all_feasible": not infeasible,
        "infeasible_runs": infeasible,
        "served_orders_range": [min(served), max(served)],
        "delivered_demand_gap_pct": demand_gap_pct,
        "tolerance": {"orders": tol_orders, "delivered_demand_pct": tol_demand_pct},
    }
    parity["ok"] = (not infeasible and max(served) - min(served) <= tol_orders
                    and demand_gap_pct <= tol_demand_pct)

    metrics = {}
    for key in METRICS:
        a = [float(r["metrics"][key]) for r in results_a]
        b = [float(r["metrics"][key]) for r in results_b]
        sa, sb = _summary(a), _summary(b)
        delta = sb["mean"] - sa["mean"]
        metrics[key] = {label_a: sa, label_b: sb, "delta": delta,
                        "delta_pct": 100.0 * delta / sa["mean"] if sa["mean"] else None,
                        "welch": _welch(a, b)}

    collinear = all(r["metrics"]["co2_distance_collinear"] for r in runs)
    n_a, n_b = len(results_a), len(results_b)
    notes = []
    if min(n_a, n_b) < MIN_SEEDS:
        notes.append(f"n<{MIN_SEEDS}: fewer than {MIN_SEEDS} runs per group; differences may be seed noise "
                     "(GVRP_Benchmark/README.md requires >= 5 seeds)")
    if collinear:
        notes.append("CO2 is a fixed multiple of distance in every run (uniform emission factor): "
                     "a CO2 difference is a distance difference")
    notes.append("Welch t is descriptive; no p-value or multiple-comparison control is applied")

    findings = []
    if parity["ok"]:
        for key in ("co2_kg", "total_cost_vnd", "distance_km", "vehicles_used"):
            m = metrics[key]
            if m["delta"] != 0:
                direction = "lower" if m["delta"] < 0 else "higher"
                pct = f" ({m['delta_pct']:+.2f} %)" if m["delta_pct"] is not None else ""
                findings.append(f"{label_b} has {direction} {key} than {label_a}: {m['delta']:+.3f}{pct}")
        verdict = "COMPARABLE"
    else:
        verdict = "SERVICE_PARITY_FAILED"

    # Cost of abatement: extra money per kg CO2 avoided when B emits less and costs more than A.
    # Only meaningful under service parity; costs are whatever the instance bundle states.
    d_cost, d_co2 = metrics["total_cost_vnd"]["delta"], metrics["co2_kg"]["delta"]
    abatement = None
    if parity["ok"] and d_co2 < 0 < d_cost:
        abatement = {"vnd_per_kg": d_cost / -d_co2, "vnd_per_t": d_cost / -d_co2 * 1000.0}
        findings.append(f"{label_b} avoids {-d_co2:.3f} kg CO2 at {abatement['vnd_per_kg']:,.0f} VND/kg "
                        f"({abatement['vnd_per_t']:,.0f} VND/t)")

    return {
        "verdict": verdict,
        "groups": {label_a: [r["route_set_meta"].get("run_id") for r in results_a],
                   label_b: [r["route_set_meta"].get("run_id") for r in results_b]},
        "instance_sha256": hashes.pop(),
        "emission_model_id": models.pop(),
        "co2_distance_collinear": collinear,
        "parity": parity,
        "metrics": metrics,
        "abatement_cost": abatement,
        "findings": findings,
        "notes": notes,
    }
