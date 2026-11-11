"""Tests for the service-parity-gated comparison (DESIGN.md §7, E-D4)."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from gvrp_eval.compare import compare  # noqa: E402
from gvrp_eval.evaluate import evaluate  # noqa: E402
from gvrp_eval.model import parse_instance, parse_route_set  # noqa: E402

FIX = ROOT / "tests" / "fixtures"


def load(name):
    return json.loads((FIX / name).read_text(encoding="utf-8"))


def scaled_routes(k: float, run_id: str) -> dict:
    """The fixture plan with every leg distance scaled by k (time unchanged)."""
    routes = load("tiny_routes.json")
    routes["meta"]["run_id"] = run_id
    for r in routes["routes"]:
        for leg in r["legs"]:
            leg["distance"] *= k
    return routes


def dropped_o2(run_id: str) -> dict:
    routes = load("tiny_routes.json")
    routes["meta"]["run_id"] = run_id
    routes["routes"].pop()
    routes["unassigned"] = ["O2"]
    return routes


def run(routes, instance=None):
    return evaluate(parse_instance(instance or load("tiny_instance.json")), parse_route_set(routes))


class Parity(unittest.TestCase):
    def test_comparable_when_service_equal(self):
        a = [run(scaled_routes(1.0, "a1"))]
        b = [run(scaled_routes(0.9, "b1"))]
        report = compare(a, b)
        self.assertEqual(report["verdict"], "COMPARABLE")
        self.assertAlmostEqual(report["metrics"]["distance_km"]["delta"], -3.5)
        self.assertAlmostEqual(report["metrics"]["co2_kg"]["delta"], -0.69)
        self.assertAlmostEqual(report["metrics"]["co2_kg"]["delta_pct"], -10.0)
        self.assertTrue(any("lower co2_kg" in f for f in report["findings"]))
        self.assertTrue(any(n.startswith("n<5") for n in report["notes"]))

    def test_less_service_is_not_reported_as_lower_emissions(self):
        a = [run(scaled_routes(1.0, "a1"))]
        b = [run(dropped_o2("b1"))]  # cheaper, same CO2 here, but serves one order fewer
        report = compare(a, b)
        self.assertEqual(report["verdict"], "SERVICE_PARITY_FAILED")
        self.assertEqual(report["findings"], [])
        self.assertEqual(report["parity"]["served_orders_range"], [2, 3])
        self.assertAlmostEqual(report["parity"]["delivered_demand_gap_pct"], 100 * 30 / 90)
        self.assertLess(report["metrics"]["total_cost_vnd"]["delta"], 0)  # still shown for diagnosis

    def test_order_tolerance_can_admit_a_declared_gap(self):
        report = compare([run(scaled_routes(1.0, "a"))], [run(dropped_o2("b"))], tol_orders=1, tol_demand_pct=40)
        self.assertEqual(report["verdict"], "COMPARABLE")

    def test_abatement_cost_only_when_cleaner_and_dearer(self):
        inst = load("tiny_instance.json")
        report = compare([run(scaled_routes(1.0, "a"), inst)], [run(scaled_routes(0.9, "b"), inst)])
        self.assertIsNone(report["abatement_cost"])  # B is cleaner AND cheaper: no trade-off
        report = compare([run(scaled_routes(0.9, "a"), inst)], [run(scaled_routes(1.0, "b"), inst)])
        self.assertIsNone(report["abatement_cost"])  # B is dirtier
        # Cleaner but dearer: lower distance cost cannot do it, so raise B's time instead.
        dearer = scaled_routes(0.9, "b")
        dearer["routes"][0]["legs"][2]["time"] += 3600  # one extra hour on V1 (60,000 VND)
        report = compare([run(scaled_routes(1.0, "a"), inst)], [run(dearer, inst)])
        d_cost = report["metrics"]["total_cost_vnd"]["delta"]
        d_co2 = report["metrics"]["co2_kg"]["delta"]
        self.assertGreater(d_cost, 0)
        self.assertAlmostEqual(report["abatement_cost"]["vnd_per_kg"], d_cost / -d_co2)
        self.assertTrue(any("avoids" in f for f in report["findings"]))

    def test_infeasible_run_fails_parity(self):
        inst = load("tiny_instance.json")
        inst["orders"][0]["tw_end"] = "08:10"
        report = compare([run(scaled_routes(1.0, "a"), inst)], [run(scaled_routes(1.0, "b"), inst)])
        self.assertEqual(report["verdict"], "SERVICE_PARITY_FAILED")
        self.assertEqual(report["parity"]["infeasible_runs"], ["a", "b"])

    def test_different_instances_are_refused(self):
        other = load("tiny_instance.json")
        other["orders"][0]["service_time_s"] = 601
        with self.assertRaises(ValueError):
            compare([run(scaled_routes(1.0, "a"))], [run(scaled_routes(1.0, "b"), other)])


class Statistics(unittest.TestCase):
    def test_group_summary_and_welch(self):
        a = [run(scaled_routes(k, f"a{k}")) for k in (1.0, 1.02, 0.98, 1.01, 0.99)]
        b = [run(scaled_routes(k, f"b{k}")) for k in (0.9, 0.92, 0.88, 0.91, 0.89)]
        report = compare(a, b, label_a="COST", label_b="ECO")
        d = report["metrics"]["distance_km"]
        self.assertEqual((d["COST"]["n"], d["ECO"]["n"]), (5, 5))
        self.assertAlmostEqual(d["COST"]["mean"], 35.0)
        self.assertAlmostEqual(d["ECO"]["mean"], 31.5)
        self.assertAlmostEqual(d["COST"]["sd"], d["ECO"]["sd"])
        # equal variances and n: t = delta / sqrt(2 sd^2 / 5), df = 8
        expected_t = -3.5 / ((2 * d["COST"]["sd"] ** 2 / 5) ** 0.5)
        self.assertAlmostEqual(d["welch"]["t"], expected_t)
        self.assertAlmostEqual(d["welch"]["df"], 8.0)
        self.assertFalse(any(n.startswith("n<5") for n in report["notes"]))

    def test_uniform_factor_note(self):
        inst = load("tiny_instance.json")
        inst["vehicle_types"][1]["emission_factor"]["value"] = 300
        report = compare([run(scaled_routes(1.0, "a"), inst)], [run(scaled_routes(0.9, "b"), inst)])
        self.assertTrue(report["co2_distance_collinear"])
        self.assertTrue(any("multiple of distance" in n for n in report["notes"]))


class Cli(unittest.TestCase):
    def test_compare_command_and_instance_mismatch(self):
        with tempfile.TemporaryDirectory() as tmp:
            a, b, out = Path(tmp) / "a.json", Path(tmp) / "b.json", Path(tmp) / "c.json"
            a.write_text(json.dumps(scaled_routes(1.0, "a")), encoding="utf-8")
            b.write_text(json.dumps(scaled_routes(0.9, "b")), encoding="utf-8")
            args = [sys.executable, "-m", "gvrp_eval", "compare", "--instance", str(FIX / "tiny_instance.json"),
                    "--a", str(a), "--b", str(b), "--out", str(out)]
            done = subprocess.run(args, cwd=ROOT, capture_output=True, text=True)
            self.assertEqual(done.returncode, 0, done.stderr)
            self.assertEqual(json.loads(out.read_text(encoding="utf-8"))["verdict"], "COMPARABLE")

            wrong = scaled_routes(0.9, "b")
            wrong["meta"]["instance_sha256"] = "0" * 64
            b.write_text(json.dumps(wrong), encoding="utf-8")
            done = subprocess.run(args, cwd=ROOT, capture_output=True, text=True)
            self.assertEqual(done.returncode, 2)


if __name__ == "__main__":
    unittest.main()
