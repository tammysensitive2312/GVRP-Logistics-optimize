"""CLI: python -m gvrp_eval evaluate --instance bundle.json --routes routes.json [--out f] [--csv f]"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

from .evaluate import evaluate
from .model import InputError, load_json, parse_instance, parse_route_set

CSV_COLUMNS = [
    "run_id", "instance_id", "instance_sha256", "producer", "producer_version", "seed",
    "evaluator_version", "emission_model_id", "time_window_mode", "feasible", "violations_total",
    "total_lateness_s", "served_orders", "unserved_orders", "delivered_demand", "total_demand",
    "vehicles_used", "eval_distance_km", "eval_co2_kg", "eval_cost_vnd", "max_abs_discrepancy_pct",
]


def csv_row(result: dict) -> dict:
    m, meta, p = result["metrics"], result["route_set_meta"], result["params"]
    disc = [d["diff_pct"] for d in result["discrepancy"].values()]
    return {
        "run_id": meta.get("run_id"), "instance_id": result["instance_id"],
        "instance_sha256": result["instance_sha256"], "producer": meta.get("producer"),
        "producer_version": meta.get("producer_version"), "seed": meta.get("seed"),
        "evaluator_version": result["evaluator_version"], "emission_model_id": p["emission_model_id"],
        "time_window_mode": p["time_window_mode"], "feasible": m["feasible"],
        "violations_total": m["violations_total"], "total_lateness_s": m["total_lateness_s"],
        "served_orders": m["served_orders"], "unserved_orders": m["unserved_orders"],
        "delivered_demand": m["delivered_demand"], "total_demand": m["total_demand"],
        "vehicles_used": m["vehicles_used"], "eval_distance_km": m["distance_km"],
        "eval_co2_kg": m["co2_kg"], "eval_cost_vnd": m["total_cost_vnd"],
        "max_abs_discrepancy_pct": max(disc) if disc else "",
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="gvrp_eval")
    sub = parser.add_subparsers(dest="command", required=True)
    ev = sub.add_parser("evaluate", help="score one route set against one instance bundle")
    ev.add_argument("--instance", required=True)
    ev.add_argument("--routes", required=True)
    ev.add_argument("--out", help="write the full evaluation JSON here (default: stdout)")
    ev.add_argument("--csv", help="append one summary row to this CSV file")
    args = parser.parse_args(argv)

    try:
        result = evaluate(parse_instance(load_json(args.instance)), parse_route_set(load_json(args.routes)))
    except InputError as exc:
        print(f"gvrp_eval: input error: {exc}", file=sys.stderr)
        return 2

    text = json.dumps(result, indent=2, ensure_ascii=False)
    if args.out:
        Path(args.out).write_text(text + "\n", encoding="utf-8")
    else:
        print(text)
    if args.csv:
        path = Path(args.csv)
        new = not path.exists() or path.stat().st_size == 0
        with path.open("a", newline="", encoding="utf-8") as fh:
            writer = csv.DictWriter(fh, fieldnames=CSV_COLUMNS)
            if new:
                writer.writeheader()
            writer.writerow(csv_row(result))
    m = result["metrics"]
    print(f"gvrp_eval: feasible={m['feasible']} served={m['served_orders']}/{m['served_orders'] + m['unserved_orders']} "
          f"distance_km={m['distance_km']:.3f} co2_kg={m['co2_kg']:.3f} violations={m['violations_total']}",
          file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
