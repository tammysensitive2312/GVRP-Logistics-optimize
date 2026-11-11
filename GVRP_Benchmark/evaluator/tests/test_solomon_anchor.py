"""Anchor: re-score the published Solomon solutions with the independent evaluator (DESIGN.md §9.3).

The reference data (GVRP_Benchmark/datasets/solomon, see its SOURCE.md) shares no code with this
repository. Under the convention of that copy (legs truncated to one decimal, travel time equal
to the truncated distance) every published solution must be feasible, serve all 100 customers
within the fleet limit, and total exactly the published cost.
"""

from __future__ import annotations

import hashlib
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from gvrp_eval import solomon  # noqa: E402
from gvrp_eval.evaluate import evaluate  # noqa: E402
from gvrp_eval.model import parse_instance, parse_route_set  # noqa: E402

DATA = ROOT.parent / "datasets" / "solomon"
INSTANCES = sorted((DATA / "raw" / "instances").glob("*.txt"))
# Published solutions that break a time window (by < 0.5 time units) when legs are NOT truncated.
# Measured 2026-11-10; a change here means the evaluator's time simulation changed.
LATE_UNDER_EXACT = {"R102", "R105", "R107", "R108", "R112", "R211", "RC101", "RC105"}


def score(name: str, convention: str):
    parsed = solomon.parse_instance_text((DATA / "raw" / "instances" / f"{name}.txt").read_text(encoding="utf-8"))
    bundle = solomon.to_bundle(parsed)
    sol = solomon.parse_solution_text((DATA / "raw" / "solutions" / f"{name}.sol").read_text(encoding="utf-8"))
    routes = solomon.to_route_set(parsed, sol, bundle, name, convention=convention)
    return parsed, sol, evaluate(parse_instance(bundle), parse_route_set(routes))


@unittest.skipUnless(INSTANCES, f"Solomon dataset not present at {DATA}")
class SolomonAnchor(unittest.TestCase):
    def test_dataset_is_complete_and_unmodified(self):
        self.assertEqual(len(INSTANCES), 56)
        for line in (DATA / "SHA256SUMS").read_text(encoding="utf-8").splitlines():
            digest, rel = line.split(maxsplit=1)
            data = (DATA / "raw" / rel.lstrip("*")).read_bytes()
            self.assertEqual(hashlib.sha256(data).hexdigest(), digest, rel)

    def test_published_solutions_reproduced_under_their_convention(self):
        for path in INSTANCES:
            with self.subTest(instance=path.stem):
                parsed, sol, r = score(path.stem, "trunc1")
                m = r["metrics"]
                self.assertTrue(m["feasible"], r["violations"])
                self.assertEqual(r["violations"], [])
                self.assertEqual(r["leg_warnings"], [])
                self.assertEqual((m["served_orders"], m["unserved_orders"]), (100, 0))
                self.assertLessEqual(m["vehicles_used"], parsed["vehicles"])
                self.assertEqual(round(solomon.benchmark_distance(r), 1), sol["reported_distance"])

    def test_exact_distances_are_longer_and_can_break_time_windows(self):
        late = set()
        for path in INSTANCES:
            _, sol, r = score(path.stem, "exact")
            self.assertGreater(solomon.benchmark_distance(r), sol["reported_distance"], path.stem)
            self.assertEqual(r["leg_warnings"], [], path.stem)
            codes = {v["code"] for v in r["violations"]}
            self.assertLessEqual(codes, {"TIME_WINDOW"}, path.stem)
            if codes:
                self.assertLess(r["metrics"]["total_lateness_s"], 0.5, path.stem)
                late.add(path.stem)
        self.assertEqual(late, LATE_UNDER_EXACT)

    def test_c101_reference_values(self):
        _, sol, r = score("C101", "trunc1")
        self.assertEqual((sol["reported_distance"], r["metrics"]["vehicles_used"]), (827.3, 10))
        _, _, exact = score("C101", "exact")
        self.assertAlmostEqual(solomon.benchmark_distance(exact), 828.9369, places=3)
        self.assertTrue(exact["metrics"]["feasible"])


if __name__ == "__main__":
    unittest.main()
