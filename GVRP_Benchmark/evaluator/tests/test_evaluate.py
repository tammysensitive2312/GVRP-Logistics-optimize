"""Hand-computed and metamorphic tests for gvrp_eval (DESIGN.md §9.1-9.2).

Hand computation for tiny_routes.json (shift start 08:00 = 28800 s):

Route V1 (T1: 5000 VND/km, 60000 VND/h, fixed 100000, 300 g/km), stops O1, O3
  depart 28800; O1 arrive 30000 (tw 08:00-09:00, no wait), service 600 -> 30600
  O3 arrive 31200, service 300 -> 31500; depot 32400. Duration 3600 s = 1 h.
  Distance 10 + 5 + 8 = 23 km. Load 40 + 20 = 60 kg <= 100.
  Cost 100000 + 23*5000 + 1*60000 = 275000. CO2 23*300/1000 = 6.9 kg.
Route V2 (T2: 1000 VND/km, 60000 VND/h, fixed 150000, 0 g/km), stop O2
  depart 28800; O2 arrive 29400, tw start 32400 -> wait 3000; service 300 -> 32700; depot 33300.
  Duration 4500 s = 1.25 h. Distance 12 km. Cost 150000 + 12000 + 75000 = 237000. CO2 0.
Totals: 35 km, 512000 VND, 6.9 kg CO2, CO2 cost 6.9/1000*150000 = 1035 VND,
  driving 2700 + 1200 = 3900 s, waiting 3000 s, service 1200 s.
"""

from __future__ import annotations

import copy
import json
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from gvrp_eval.evaluate import evaluate  # noqa: E402
from gvrp_eval.model import InputError, parse_instance, parse_route_set  # noqa: E402

FIX = ROOT / "tests" / "fixtures"


def load(name):
    return json.loads((FIX / name).read_text(encoding="utf-8"))


def run(instance=None, routes=None):
    return evaluate(parse_instance(instance or load("tiny_instance.json")),
                    parse_route_set(routes or load("tiny_routes.json")))


def codes(result):
    return sorted(v["code"] for v in result["violations"])


class HandComputed(unittest.TestCase):
    def test_baseline_totals(self):
        r = run()
        m = r["metrics"]
        self.assertTrue(m["feasible"])
        self.assertEqual(codes(r), [])
        self.assertEqual((m["served_orders"], m["unserved_orders"], m["vehicles_used"]), (3, 0, 2))
        self.assertAlmostEqual(m["delivered_demand"], 90)
        self.assertAlmostEqual(m["served_demand_pct"], 100.0)
        self.assertAlmostEqual(m["distance_km"], 35.0)
        self.assertAlmostEqual(m["total_cost_vnd"], 512000.0)
        self.assertAlmostEqual(m["fixed_cost_vnd"], 250000.0)
        self.assertAlmostEqual(m["distance_cost_vnd"], 127000.0)
        self.assertAlmostEqual(m["time_cost_vnd"], 135000.0)
        self.assertAlmostEqual(m["driving_time_cost_vnd"], 3900 / 3600 * 60000)
        self.assertAlmostEqual(m["co2_kg"], 6.9)
        self.assertAlmostEqual(m["co2_cost_vnd"], 1035.0)
        self.assertAlmostEqual(m["driving_h"] * 3600, 3900)
        self.assertAlmostEqual(m["waiting_h"] * 3600, 3000)
        self.assertAlmostEqual(m["service_h"] * 3600, 1200)
        self.assertFalse(m["co2_distance_collinear"])

    def test_schedule_of_route_two(self):
        stop = run()["routes"][1]["stops"][0]
        self.assertEqual((stop["arrival_s"], stop["wait_s"], stop["service_start_s"], stop["departure_s"]),
                         (29400, 3000, 32400, 32700))
        self.assertEqual(run()["routes"][1]["end_s"], 33300)

    def test_discrepancy_against_reported(self):
        d = run()["discrepancy"]
        self.assertAlmostEqual(d["distance_km"]["diff"], 0.0)
        self.assertAlmostEqual(d["co2_kg"]["diff"], -0.1)
        self.assertAlmostEqual(d["co2_kg"]["diff_pct"], 0.1 / 6.9 * 100)


class Violations(unittest.TestCase):
    def test_late_arrival_strict_and_soft(self):
        inst = load("tiny_instance.json")
        inst["orders"][0]["tw_end"] = "08:10"  # O1 service starts 08:20 -> 600 s late
        r = run(instance=inst)
        self.assertEqual(codes(r), ["TIME_WINDOW"])
        self.assertEqual(r["metrics"]["total_lateness_s"], 600)
        self.assertFalse(r["metrics"]["feasible"])
        inst["params"]["time_window_mode"] = "SOFT"
        r = run(instance=inst)
        self.assertTrue(r["metrics"]["feasible"])
        self.assertEqual(r["metrics"]["total_lateness_s"], 600)

    def test_over_capacity(self):
        inst = load("tiny_instance.json")
        inst["orders"][0]["demand"]["value"] = 90  # 90 + 20 = 110 > 100
        r = run(instance=inst)
        self.assertEqual(codes(r), ["OVER_CAPACITY"])
        self.assertAlmostEqual(r["violations"][0]["amount"], 10)

    def test_skill_and_lez_skill_mismatch(self):
        routes = load("tiny_routes.json")
        routes["routes"][0]["stops"] = ["O1", "O2"]  # O2 needs lez-a, V1 lacks it
        routes["routes"][1]["stops"] = ["O3"]  # O3 needs cold, V2 lacks it
        self.assertEqual(codes(run(routes=routes)), ["LEZ_SKILL_MISMATCH", "SKILL_MISMATCH"])

    def test_coverage_duplicate_missing_unknown(self):
        routes = load("tiny_routes.json")
        routes["unassigned"] = ["O1", "O9"]
        routes["routes"][1]["stops"] = ["O3"]
        routes["routes"][0]["stops"] = ["O1", "O3"]
        got = codes(run(routes=routes))
        self.assertEqual(got, ["COVERAGE_DUPLICATE", "COVERAGE_DUPLICATE", "COVERAGE_MISSING",
                               "COVERAGE_UNKNOWN", "SKILL_MISMATCH"])

    def test_vehicle_reused_and_unknown(self):
        routes = load("tiny_routes.json")
        routes["routes"][1]["vehicle_id"] = "V1"
        self.assertIn("VEHICLE_REUSED", codes(run(routes=routes)))
        routes["routes"][1]["vehicle_id"] = "V9"
        self.assertIn("VEHICLE_UNKNOWN", codes(run(routes=routes)))

    def test_max_duration_max_distance_shift_end(self):
        inst = load("tiny_instance.json")
        inst["vehicle_types"][1]["max_duration_h"] = 1.0  # route V2 lasts 1.25 h
        inst["vehicle_types"][1]["max_distance_km"] = 10  # route V2 is 12 km
        inst["vehicles"][0]["shift_end"] = "08:50"  # route V1 ends 09:00
        r = run(instance=inst)
        self.assertEqual(codes(r), ["MAX_DISTANCE", "MAX_DURATION", "SHIFT_END"])
        amounts = {v["code"]: v["amount"] for v in r["violations"]}
        self.assertAlmostEqual(amounts["MAX_DURATION"], 900)
        self.assertAlmostEqual(amounts["MAX_DISTANCE"], 2)
        self.assertAlmostEqual(amounts["SHIFT_END"], 600)


class InputValidation(unittest.TestCase):
    def assert_input_error(self, instance=None, routes=None):
        with self.assertRaises(InputError):
            run(instance=instance, routes=routes)

    def test_null_emission_factor_is_an_error_not_a_default(self):
        inst = load("tiny_instance.json")
        inst["vehicle_types"][0]["emission_factor"]["value"] = None
        self.assert_input_error(instance=inst)

    def test_emission_unit_and_provenance_required(self):
        inst = load("tiny_instance.json")
        inst["vehicle_types"][0]["emission_factor"]["unit"] = "l_per_100km"
        self.assert_input_error(instance=inst)
        inst = load("tiny_instance.json")
        inst["vehicle_types"][0]["emission_factor"]["provenance"] = ""
        self.assert_input_error(instance=inst)

    def test_demand_unit_must_match_capacity_unit(self):
        inst = load("tiny_instance.json")
        inst["orders"][0]["demand"]["unit"] = "m3"
        self.assert_input_error(instance=inst)

    def test_leg_count_and_units(self):
        routes = load("tiny_routes.json")
        routes["routes"][0]["legs"].pop()
        self.assert_input_error(routes=routes)
        routes = load("tiny_routes.json")
        routes["leg_units"] = {"distance": "mile", "time": "s"}
        self.assert_input_error(routes=routes)
        routes = load("tiny_routes.json")
        del routes["leg_units"]
        self.assert_input_error(routes=routes)

    def test_km_and_minutes_are_converted(self):
        routes = load("tiny_routes.json")
        routes["leg_units"] = {"distance": "km", "time": "min"}
        for r in routes["routes"]:
            for leg in r["legs"]:
                leg["distance"] /= 1000
                leg["time"] /= 60
        self.assertAlmostEqual(run(routes=routes)["metrics"]["total_cost_vnd"], 512000.0)

    def test_unknown_emission_model(self):
        inst = load("tiny_instance.json")
        inst["params"]["emission_model_id"] = "EM9"
        self.assert_input_error(instance=inst)


class Metamorphic(unittest.TestCase):
    def test_scaling_leg_distance_scales_distance_and_co2(self):
        base = run()["metrics"]
        routes = load("tiny_routes.json")
        for r in routes["routes"]:
            for leg in r["legs"]:
                leg["distance"] *= 3
        m = run(routes=routes)["metrics"]
        self.assertAlmostEqual(m["distance_km"], 3 * base["distance_km"])
        self.assertAlmostEqual(m["co2_kg"], 3 * base["co2_kg"])
        self.assertEqual(m["served_orders"], base["served_orders"])
        self.assertAlmostEqual(m["time_cost_vnd"], base["time_cost_vnd"])

    def test_route_order_does_not_change_totals(self):
        base = run()["metrics"]
        routes = load("tiny_routes.json")
        routes["routes"].reverse()
        m = run(routes=routes)["metrics"]
        for key in ("distance_km", "co2_kg", "total_cost_vnd", "served_orders", "waiting_h"):
            self.assertAlmostEqual(m[key], base[key])

    def test_unassigning_an_order_changes_service_metrics(self):
        routes = load("tiny_routes.json")
        routes["routes"].pop()  # drop V2 route, O2 becomes unassigned
        routes["unassigned"] = ["O2"]
        m = run(routes=routes)["metrics"]
        self.assertTrue(m["feasible"])
        self.assertEqual((m["served_orders"], m["unserved_orders"], m["vehicles_used"]), (2, 1, 1))
        self.assertAlmostEqual(m["delivered_demand"], 60)
        self.assertAlmostEqual(m["distance_km"], 23)
        self.assertTrue(m["co2_distance_collinear"])

    def test_uniform_factor_is_flagged_collinear(self):
        inst = load("tiny_instance.json")
        inst["vehicle_types"][1]["emission_factor"]["value"] = 300
        self.assertTrue(run(instance=inst)["metrics"]["co2_distance_collinear"])

    def test_instance_hash_ignores_its_own_meta_hash(self):
        a = load("tiny_instance.json")
        b = copy.deepcopy(a)
        b["meta"]["sha256"] = "anything"
        self.assertEqual(parse_instance(a).sha256, parse_instance(b).sha256)
        b["orders"][0]["service_time_s"] = 601
        self.assertNotEqual(parse_instance(a).sha256, parse_instance(b).sha256)


class Cli(unittest.TestCase):
    def test_evaluate_writes_json_and_csv(self):
        import tempfile
        with tempfile.TemporaryDirectory() as tmp:
            out, table = Path(tmp) / "e.json", Path(tmp) / "r.csv"
            args = [sys.executable, "-m", "gvrp_eval", "evaluate", "--instance", str(FIX / "tiny_instance.json"),
                    "--routes", str(FIX / "tiny_routes.json"), "--out", str(out), "--csv", str(table)]
            for _ in range(2):
                done = subprocess.run(args, cwd=ROOT, capture_output=True, text=True)
                self.assertEqual(done.returncode, 0, done.stderr)
            self.assertAlmostEqual(json.loads(out.read_text(encoding="utf-8"))["metrics"]["distance_km"], 35.0)
            lines = table.read_text(encoding="utf-8").splitlines()
            self.assertEqual(len(lines), 3)  # header written once
            self.assertTrue(lines[0].startswith("run_id,instance_id"))

    def test_input_error_exit_code(self):
        done = subprocess.run([sys.executable, "-m", "gvrp_eval", "evaluate", "--instance",
                               str(FIX / "tiny_routes.json"), "--routes", str(FIX / "tiny_routes.json")],
                              cwd=ROOT, capture_output=True, text=True)
        self.assertEqual(done.returncode, 2)
        self.assertIn("input error", done.stderr)


if __name__ == "__main__":
    unittest.main()
