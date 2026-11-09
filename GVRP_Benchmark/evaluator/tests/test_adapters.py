"""Adapter and leg-sanity tests on synthetic data (no customer data in the repository).

The synthetic Entry export and Engine callback encode the same plan as tiny_routes.json, so the
converted result must score exactly like the hand-computed fixture.
"""

from __future__ import annotations

import copy
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from gvrp_eval.adapters import from_engine_callback, from_entry_export, input_drift  # noqa: E402
from gvrp_eval.evaluate import evaluate  # noqa: E402
from gvrp_eval.model import InputError, Leg, parse_instance, parse_route_set  # noqa: E402
from gvrp_eval.sanity import check_legs, great_circle_m  # noqa: E402

FIX = ROOT / "tests" / "fixtures"


def load(name):
    return json.loads((FIX / name).read_text(encoding="utf-8"))


def depot(arrival, departure, d, t):
    return {"type": "DEPOT", "order_id": None, "sequence_number": None,
            "arrival_time": arrival, "departure_time": departure, "distance_to_next": d, "time_to_next": t}


def order(oid, seq, d, t):
    return {"type": "ORDER", "order_id": oid, "sequence_number": seq,
            "arrival_time": None, "departure_time": None, "distance_to_next": d, "time_to_next": t}


def entry_export():
    """Same plan as tiny_routes.json, in the shape of sql/export_solution.sql (stops unordered)."""
    return {
        "solution": {"id": 7, "job_id": 70, "branch_id": 1, "type": "ENGINE_GENERATED",
                     "total_distance": 35.0, "total_co2": 6.9, "total_cost": 512000.0,
                     "served_orders": 3, "unserved_orders": 0, "total_vehicles_used": 2},
        "job_input": {"order_ids": [1, 2, 3], "vehicle_ids": [11, 12],
                      "preferences": {"time_window_mode": "STRICT", "seed": 42}},
        "orders": [
            {"id": 1, "lat": 21.05, "lng": 105.8, "demand": 40, "service_time": 10,
             "tw_start": "08:00:00", "tw_end": "09:00:00", "required_skills": None},
            {"id": 2, "lat": 21.0, "lng": 105.84, "demand": 30, "service_time": 5,
             "tw_start": "09:00:00", "tw_end": "10:00:00", "required_skills": []},
            {"id": 3, "lat": 21.03, "lng": 105.8, "demand": 20, "service_time": 5,
             "tw_start": None, "tw_end": None, "required_skills": ["cold"]},
        ],
        "vehicles": [
            {"id": 11, "vehicle_type_id": 101, "start_depot_id": 5, "end_depot_id": 5},
            {"id": 12, "vehicle_type_id": 102, "start_depot_id": 5, "end_depot_id": 5},
        ],
        "vehicle_types": [
            {"id": 101, "capacity": 100, "fixed_cost": 100000, "cost_per_km": 5000, "cost_per_hour": 60000,
             "max_distance": None, "max_duration": 8, "vehicle_features": {"skills": ["cold"], "emission_factor": 300}},
            {"id": 102, "capacity": 50, "fixed_cost": 150000, "cost_per_km": 1000, "cost_per_hour": 60000,
             "max_distance": 30, "max_duration": None, "vehicle_features": {"skills": [], "emission_factor": 0.0}},
        ],
        "depots": [{"id": 5, "lat": 21.0, "lng": 105.8}],
        "routes": [
            {"id": 900, "vehicle_id": 12, "route_order": 2, "stops": [
                depot("09:15:00", None, 0, 0), order(2, 1, 6000, 600), depot("08:00:00", "08:00:00", 6000, 600)]},
            {"id": 901, "vehicle_id": 11, "route_order": 1, "stops": [
                order(3, 2, 8000, 900), depot("08:00:00", "08:00:00", 10000, 1200),
                order(1, 1, 5000, 600), depot("09:00:00", None, 0, 0)]},
        ],
        "unassigned": None,
        "job_created_at": "2026-11-01T10:00:00",
        "inputs_updated_after_job": {"vehicle_types": None, "vehicles": 0, "orders": 0},
    }


class EntryExport(unittest.TestCase):
    def convert(self, export=None):
        bundle, routes = from_entry_export(export or entry_export(), 150000)
        return bundle, routes, evaluate(parse_instance(bundle), parse_route_set(routes))

    def test_reproduces_hand_computed_totals_and_reported_values(self):
        _, routes, r = self.convert()
        m = r["metrics"]
        self.assertTrue(m["feasible"])
        self.assertAlmostEqual(m["distance_km"], 35.0)
        self.assertAlmostEqual(m["co2_kg"], 6.9)
        # V2's type has no max_duration -> Engine default 12 h; costs are unaffected.
        self.assertAlmostEqual(m["total_cost_vnd"], 512000.0)
        self.assertEqual(m["leg_warnings_total"], 0)
        self.assertTrue(all(abs(d["diff"]) < 1e-9 for d in r["discrepancy"].values()))
        self.assertEqual([x["stops"] for x in routes["routes"]], [["1", "3"], ["2"]])
        self.assertEqual(routes["meta"]["seed"], 42)

    def test_minutes_to_seconds_and_engine_shift(self):
        bundle, _, _ = self.convert()
        self.assertEqual([o["service_time_s"] for o in bundle["orders"]], [600, 300, 300])
        shifts = {v["id"]: (v["shift_start"], v["shift_end"]) for v in bundle["vehicles"]}
        self.assertEqual(shifts, {"11": ("08:00:00", "16:00:00"), "12": ("08:00:00", "20:00:00")})

    def test_bundle_hash_matches_evaluator_hash(self):
        bundle, routes, r = self.convert()
        self.assertEqual(r["instance_sha256"], bundle["meta"]["sha256"])
        self.assertEqual(routes["meta"]["instance_sha256"], bundle["meta"]["sha256"])

    def test_missing_time_window_mode_is_rejected(self):
        export = entry_export()
        del export["job_input"]["preferences"]["time_window_mode"]
        with self.assertRaises(InputError):
            from_entry_export(export, 150000)

    def test_null_emission_factor_reaches_the_evaluator_as_an_error(self):
        export = entry_export()
        export["vehicle_types"][0]["vehicle_features"]["emission_factor"] = None
        bundle, _ = from_entry_export(export, 150000)
        with self.assertRaises(InputError):
            parse_instance(bundle)

    def test_route_without_two_depots_is_rejected(self):
        export = entry_export()
        export["routes"][0]["stops"].pop(0)
        with self.assertRaises(InputError):
            from_entry_export(export, 150000)

    def test_input_drift_flags_edited_vehicle_types(self):
        self.assertFalse(input_drift(entry_export())["suspect"])
        export = entry_export()
        export["inputs_updated_after_job"] = {"vehicle_types": [102], "vehicles": 0, "orders": 3}
        _, routes = from_entry_export(export, 150000)
        drift = routes["meta"]["input_drift"]
        self.assertTrue(drift["suspect"])
        self.assertEqual((drift["vehicle_types"], drift["orders"]), (["102"], 3))
        only_orders = entry_export()
        only_orders["inputs_updated_after_job"] = {"vehicle_types": None, "vehicles": 0, "orders": 3}
        self.assertFalse(input_drift(only_orders)["suspect"])


class EngineCallback(unittest.TestCase):
    def payload(self):
        return {"solution_data": {
            "total_distance": 35.0, "total_co2": 6.9, "total_cost": 512000.0,
            "served_orders": 3, "unserved_orders": 0, "total_vehicles_used": 2,
            "routes": [
                {"vehicle_id": "V1", "stops": [
                    {"type": "DEPOT", "departure_time": "08:00:00", "distance_to_next": 10000, "time_to_next": 1200},
                    {"type": "CUSTOMER", "order_id": "O1", "distance_to_next": 5000, "time_to_next": 600},
                    {"type": "CUSTOMER", "order_id": "O3", "distance_to_next": 8000, "time_to_next": 900},
                    {"type": "DEPOT", "distance_to_next": 0.0, "time_to_next": 0.0}]},
                {"vehicle_id": "V2", "stops": [
                    {"type": "DEPOT", "departure_time": "08:00:00", "distance_to_next": 6000, "time_to_next": 600},
                    {"type": "CUSTOMER", "order_id": "O2", "distance_to_next": 6000, "time_to_next": 600},
                    {"type": "DEPOT", "distance_to_next": 0.0, "time_to_next": 0.0}]},
            ],
            "unassigned_orders": [],
        }}

    def test_callback_scores_like_the_fixture(self):
        routes = from_engine_callback(self.payload(), "cb-1")
        r = evaluate(parse_instance(load("tiny_instance.json")), parse_route_set(routes))
        self.assertTrue(r["metrics"]["feasible"])
        self.assertAlmostEqual(r["metrics"]["total_cost_vnd"], 512000.0)
        self.assertEqual(routes["meta"]["run_id"], "cb-1")

    def test_matrix_fallback_zero_leg_is_flagged(self):
        payload = self.payload()
        payload["solution_data"]["routes"][0]["stops"][1]["distance_to_next"] = 0.0
        payload["solution_data"]["routes"][0]["stops"][1]["time_to_next"] = 0.0
        r = evaluate(parse_instance(load("tiny_instance.json")), parse_route_set(from_engine_callback(payload)))
        self.assertEqual(r["metrics"]["leg_warnings_by_code"], {"LEG_ZERO_DISTINCT": 1})
        self.assertTrue(r["metrics"]["feasible"])  # a warning, not a violation

    def test_unassigned_objects_and_ids(self):
        payload = self.payload()
        payload["solution_data"]["routes"].pop()
        payload["solution_data"]["unassigned_orders"] = [{"order_id": "O2", "reason": "x"}]
        self.assertEqual(from_engine_callback(payload)["unassigned"], ["O2"])


class LegSanity(unittest.TestCase):
    def test_great_circle_one_degree_latitude(self):
        self.assertAlmostEqual(great_circle_m(0, 0, 1, 0), 111_195, delta=5)

    def test_codes(self):
        pts = [("a", 21.0, 105.8), ("b", 21.01, 105.8)]  # ~1112 m apart
        self.assertEqual(check_legs(pts, [Leg(1500, 120)], 0), [])
        self.assertEqual(check_legs(pts, [Leg(900, 120)], 0)[0]["code"], "LEG_SHORTER_THAN_GREAT_CIRCLE")
        self.assertEqual(check_legs(pts, [Leg(0, 0)], 0)[0]["code"], "LEG_ZERO_DISTINCT")
        self.assertEqual(check_legs(pts, [Leg(1500, 0)], 0)[0]["code"], "LEG_ZERO_TIME")
        self.assertEqual(check_legs(pts, [Leg(1500, 10)], 0)[0]["code"], "LEG_SPEED")  # 540 km/h
        same = [("a", 21.0, 105.8), ("a2", 21.0, 105.8)]
        self.assertEqual(check_legs(same, [Leg(0, 0)], 0), [])


if __name__ == "__main__":
    unittest.main()
