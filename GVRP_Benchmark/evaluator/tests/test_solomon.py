"""Solomon-format adapter tests on a hand-computed toy instance (no benchmark data needed).

TOY: depot (0,0) open [0,100]; 2 vehicles, capacity 30.
  1: (3,4) demand 10, window [0,20],  service 5
  2: (6,8) demand 15, window [30,50], service 5
  3: (0,5) demand 20, window [0,90],  service 10
Solution: route 1 = 1 2, route 2 = 3.
  Route 1: 0->1 = 5, arrive 5, leave 10; 1->2 = 5, arrive 15, wait 15, start 30, leave 35;
           2->0 = 10, back at 45. Distance 20, load 25.
  Route 2: 0->3 = 5, arrive 5, leave 15; 3->0 = 5, back at 20. Distance 10, load 20.
Totals: distance 30, driving 30, waiting 15, service 20.
"""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from gvrp_eval import solomon  # noqa: E402
from gvrp_eval.evaluate import evaluate  # noqa: E402
from gvrp_eval.model import InputError, Leg, parse_instance, parse_route_set  # noqa: E402
from gvrp_eval.sanity import check_legs  # noqa: E402

TOY = """TOY

VEHICLE
NUMBER     CAPACITY
   2          30

CUSTOMER
CUST NO.  XCOORD.   YCOORD.    DEMAND   READY TIME  DUE DATE   SERVICE   TIME

    0       0          0          0          0        100          0
    1       3          4         10          0         20          5
    2       6          8         15         30         50          5
    3       0          5         20          0         90         10
"""
SOLUTION = "Route 1 : 1 2\nRoute 2 : 3\nCost 30.00\n"


def score(instance_text=TOY, solution_text=SOLUTION):
    parsed = solomon.parse_instance_text(instance_text)
    bundle = solomon.to_bundle(parsed)
    routes = solomon.to_route_set(parsed, solomon.parse_solution_text(solution_text), bundle, "toy")
    return evaluate(parse_instance(bundle), parse_route_set(routes))


def codes(result):
    return sorted(v["code"] for v in result["violations"])


class Toy(unittest.TestCase):
    def test_hand_computed_solution(self):
        r = score()
        m = r["metrics"]
        self.assertTrue(m["feasible"])
        self.assertEqual(codes(r), [])
        self.assertEqual(m["leg_warnings_total"], 0)
        self.assertAlmostEqual(solomon.benchmark_distance(r), 30.0)
        self.assertEqual((m["served_orders"], m["unserved_orders"], m["vehicles_used"]), (3, 0, 2))
        self.assertAlmostEqual(m["driving_h"] * 3600, 30)
        self.assertAlmostEqual(m["waiting_h"] * 3600, 15)
        self.assertAlmostEqual(m["service_h"] * 3600, 20)
        self.assertEqual([x["end_s"] for x in r["routes"]], [45, 20])
        self.assertAlmostEqual(r["discrepancy"]["distance_km"]["diff"], 0.0)
        self.assertEqual((m["co2_kg"], m["total_cost_vnd"]), (0.0, 0.0))

    def test_parsed_fields(self):
        parsed = solomon.parse_instance_text(TOY)
        self.assertEqual((parsed["name"], parsed["vehicles"], parsed["capacity"]), ("TOY", 2, 30.0))
        self.assertEqual([c["id"] for c in parsed["customers"]], [1, 2, 3])
        bundle = solomon.to_bundle(parsed)
        self.assertEqual(bundle["params"]["coordinates"], "planar")
        self.assertEqual(bundle["vehicles"][0]["shift_end"], "00:01:40")
        self.assertEqual(bundle["orders"][1]["tw_start"], "00:00:30")

    def test_late_arrival_when_order_is_swapped(self):
        # 0->2 = 10, wait to 30, leave 35; 2->1 = 5, arrive 40 > due 20 -> 20 late
        r = score(solution_text="Route 1 : 2 1\nRoute 2 : 3\n")
        self.assertEqual(codes(r), ["TIME_WINDOW"])
        self.assertEqual(r["metrics"]["total_lateness_s"], 20)
        self.assertFalse(r["metrics"]["feasible"])

    def test_over_capacity(self):
        r = score(solution_text="Route 1 : 1 2 3\n")
        self.assertIn("OVER_CAPACITY", codes(r))
        amount = [v["amount"] for v in r["violations"] if v["code"] == "OVER_CAPACITY"][0]
        self.assertAlmostEqual(amount, 15)

    def test_depot_due_date_is_the_shift_end(self):
        r = score(instance_text=TOY.replace("0        100          0", "0         40          0"))
        self.assertEqual(codes(r), ["SHIFT_END"])
        self.assertAlmostEqual(r["violations"][0]["amount"], 5)

    def test_missing_customer_is_unserved(self):
        m = score(solution_text="Route 1 : 1 2\n")["metrics"]
        self.assertEqual((m["served_orders"], m["unserved_orders"]), (2, 1))

    def test_depot_zeros_and_alternative_spelling(self):
        sol = solomon.parse_solution_text("route #1: 0 1 2 0\nRoute 2: 3\nDistance: 30\n")
        self.assertEqual(sol, {"routes": [[1, 2], [3]], "reported_distance": 30.0})

    def test_rejections(self):
        parsed = solomon.parse_instance_text(TOY)
        bundle = solomon.to_bundle(parsed)
        with self.assertRaises(InputError):  # more routes than vehicles
            solomon.to_route_set(parsed, {"routes": [[1], [2], [3]], "reported_distance": None}, bundle)
        with self.assertRaises(InputError):  # unknown customer
            solomon.to_route_set(parsed, {"routes": [[1, 9]], "reported_distance": None}, bundle)
        with self.assertRaises(InputError):
            solomon.parse_solution_text("no routes here")
        with self.assertRaises(InputError):
            solomon.parse_instance_text("X\nCUSTOMER\n 0 0 0 0 0 100 0\n")  # no VEHICLE block
        with self.assertRaises(InputError):  # fractional time cannot be mapped to whole seconds
            solomon.to_bundle(solomon.parse_instance_text(TOY.replace("30         50", "30.5       50")))


class Conventions(unittest.TestCase):
    def test_trunc1_truncates_each_leg_and_never_rounds_up(self):
        self.assertEqual(solomon.leg_distance((0, 0), (1, 1), "trunc1"), 1.4)  # 1.4142
        self.assertEqual(solomon.leg_distance((0, 0), (2, 2), "trunc1"), 2.8)  # 2.8284
        self.assertEqual(solomon.leg_distance((0, 0), (9, 4), "trunc1"), 9.8)  # 9.8489, not 9.9
        self.assertEqual(solomon.leg_distance((0, 0), (3, 4), "trunc1"), 5.0)
        self.assertAlmostEqual(solomon.leg_distance((0, 0), (1, 1), "exact"), 2 ** 0.5)

    def test_truncated_route_set_declares_its_truncation(self):
        text = TOY.replace("3          4         10", "1          1         10")  # customer 1 at (1,1)
        parsed = solomon.parse_instance_text(text)
        bundle = solomon.to_bundle(parsed)
        sol = solomon.parse_solution_text("Route 1 : 1\nRoute 2 : 3\n")
        routes = solomon.to_route_set(parsed, sol, bundle, convention="trunc1")
        self.assertEqual(routes["leg_units"]["distance_truncation"], 0.1)
        self.assertEqual(routes["routes"][0]["legs"][0], {"distance": 1.4, "time": 1.4})
        r = evaluate(parse_instance(bundle), parse_route_set(routes))
        self.assertEqual(r["metrics"]["leg_warnings_total"], 0)
        self.assertAlmostEqual(solomon.benchmark_distance(r), 1.4 + 1.4 + 10)
        routes["leg_units"]["distance_truncation"] = 0.0  # same legs, truncation not declared
        r = evaluate(parse_instance(bundle), parse_route_set(routes))
        self.assertEqual(r["metrics"]["leg_warnings_by_code"], {"LEG_SHORTER_THAN_GREAT_CIRCLE": 2})
        with self.assertRaises(InputError):
            solomon.to_route_set(parsed, sol, bundle, convention="round2")


class PlanarSanity(unittest.TestCase):
    def test_truncated_leg_is_flagged_only_in_planar_mode(self):
        pts = [("a", 0.0, 0.0), ("b", 4.0, 3.0)]  # Euclidean distance 5
        self.assertEqual(check_legs(pts, [Leg(5.0, 5.0)], 0, planar=True), [])
        self.assertEqual(check_legs(pts, [Leg(4.9, 4.9)], 0, planar=True)[0]["code"],
                         "LEG_SHORTER_THAN_GREAT_CIRCLE")
        self.assertEqual(check_legs(pts, [Leg(0.0, 0.0)], 0, planar=True)[0]["code"], "LEG_ZERO_DISTINCT")

    def test_planar_coordinates_outside_degree_range_need_planar_mode(self):
        bundle = solomon.to_bundle(solomon.parse_instance_text(TOY.replace("0          5         20", "0         95         20")))
        parse_instance(bundle)  # planar: accepted
        bundle["params"]["coordinates"] = "wgs84"
        with self.assertRaises(InputError):
            parse_instance(bundle)
        del bundle["params"]["coordinates"]
        with self.assertRaises(InputError):
            parse_instance(bundle)


class Cli(unittest.TestCase):
    def test_convert_then_evaluate(self):
        with tempfile.TemporaryDirectory() as tmp:
            t = Path(tmp)
            (t / "toy.txt").write_text(TOY, encoding="utf-8")
            (t / "toy.sol").write_text(SOLUTION, encoding="utf-8")
            base = [sys.executable, "-m", "gvrp_eval"]
            done = subprocess.run(base + ["convert", "--from", "solomon", str(t / "toy.txt"), "--solution",
                                          str(t / "toy.sol"), "--instance-out", str(t / "b.json"),
                                          "--routes-out", str(t / "r.json"),
                                          "--distance-convention", "exact"],
                                  cwd=ROOT, capture_output=True, text=True)
            self.assertEqual(done.returncode, 0, done.stderr)
            done = subprocess.run(base + ["evaluate", "--instance", str(t / "b.json"), "--routes",
                                          str(t / "r.json"), "--out", str(t / "e.json")],
                                  cwd=ROOT, capture_output=True, text=True)
            self.assertEqual(done.returncode, 0, done.stderr)
            result = json.loads((t / "e.json").read_text(encoding="utf-8"))
            self.assertAlmostEqual(solomon.benchmark_distance(result), 30.0)
            done = subprocess.run(base + ["convert", "--from", "solomon", str(t / "toy.txt"),
                                          "--solution", str(t / "toy.sol"), "--instance-out", str(t / "b.json")],
                                  cwd=ROOT, capture_output=True, text=True)
            self.assertEqual(done.returncode, 2)  # --solution without --routes-out
            done = subprocess.run(base + ["convert", "--from", "solomon", str(t / "toy.txt"), "--solution",
                                          str(t / "toy.sol"), "--instance-out", str(t / "b.json"),
                                          "--routes-out", str(t / "r.json")],
                                  cwd=ROOT, capture_output=True, text=True)
            self.assertEqual(done.returncode, 2)  # convention must be stated


if __name__ == "__main__":
    unittest.main()
