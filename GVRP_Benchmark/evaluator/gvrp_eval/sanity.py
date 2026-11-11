"""Leg sanity checks (DESIGN.md §4.7): warnings, never infeasibility.

They bound the one input the evaluator shares with the solver, the road-network legs.
A road leg cannot be shorter than the great-circle distance between its endpoints, and
its implied speed must be physically plausible. A 0 m leg between distinct points is the
signature of a silent matrix-lookup fallback.
"""

from __future__ import annotations

import math

EARTH_RADIUS_M = 6_371_008.8  # mean Earth radius (IUGG)
GC_TOLERANCE = 0.01  # road may be 1 % "shorter" than great-circle (coordinate rounding)
GC_SLACK_M = 5.0
ZERO_LEG_MIN_GC_M = 10.0
SPEED_MIN_KMH, SPEED_MAX_KMH = 1.0, 120.0
SPEED_CHECK_MIN_M = 100.0  # very short legs have noisy times


def great_circle_m(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lng2 - lng1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * EARTH_RADIUS_M * math.asin(min(1.0, math.sqrt(a)))


def check_legs(points: list[tuple[str, float, float]], legs, route_index: int, planar: bool = False,
               truncation: float = 0.0) -> list[dict]:
    """points: [(label, lat, lng)] depot, stops..., depot; legs: len(points) - 1.

    For planar instances the lower bound is the Euclidean distance, in the leg's own unit.
    `truncation` is the producer's declared leg truncation (same unit); it widens the bound.
    """
    warnings = []
    # Planar legs must respect the Euclidean bound exactly (float noise only); road legs get slack
    # for coordinate rounding and map matching.
    tolerance, slack, zero_min = (0.0, 1e-9, 1e-9) if planar else (GC_TOLERANCE, GC_SLACK_M, ZERO_LEG_MIN_GC_M)
    slack += truncation
    zero_min += truncation
    for i, leg in enumerate(legs):
        (a, lat1, lng1), (b, lat2, lng2) = points[i], points[i + 1]
        gc = math.hypot(lat2 - lat1, lng2 - lng1) if planar else great_circle_m(lat1, lng1, lat2, lng2)
        base = {"route_index": route_index, "leg_index": i, "from": a, "to": b,
                "distance_m": leg.distance_m, "time_s": leg.time_s, "great_circle_m": round(gc, 6 if planar else 1)}
        if leg.distance_m == 0 and gc > zero_min:
            warnings.append({"code": "LEG_ZERO_DISTINCT", **base})
            continue
        if leg.distance_m < gc * (1 - tolerance) - slack:
            warnings.append({"code": "LEG_SHORTER_THAN_GREAT_CIRCLE", **base})
        if leg.distance_m >= SPEED_CHECK_MIN_M:
            if leg.time_s <= 0:
                warnings.append({"code": "LEG_ZERO_TIME", **base})
            else:
                kmh = leg.distance_m / leg.time_s * 3.6
                if not SPEED_MIN_KMH <= kmh <= SPEED_MAX_KMH:
                    warnings.append({"code": "LEG_SPEED", "speed_kmh": round(kmh, 1), **base})
    return warnings
