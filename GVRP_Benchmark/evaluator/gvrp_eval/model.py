"""Instance bundle and route-set loading with explicit unit validation.

Every quantity carries a declared unit. Nothing is defaulted silently: a missing or
unknown field raises InputError (DESIGN.md §1, rule 2 and 3).
"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


class InputError(ValueError):
    """The instance or route set is malformed; no metrics can be trusted."""


EMISSION_UNIT = "g_co2_per_km"
DISTANCE_TO_M = {"m": 1.0, "km": 1000.0}
TIME_TO_S = {"s": 1.0, "min": 60.0}
TW_MODES = ("STRICT", "SOFT")
COORDINATE_SYSTEMS = ("wgs84", "planar")


def _req(obj: dict, key: str, where: str) -> Any:
    if key not in obj or obj[key] is None:
        raise InputError(f"{where}: missing required field '{key}'")
    return obj[key]


def _num(obj: dict, key: str, where: str) -> float:
    value = _req(obj, key, where)
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise InputError(f"{where}: '{key}' must be a number")
    if value < 0:
        raise InputError(f"{where}: '{key}' must be >= 0")
    return float(value)


def _opt_num(obj: dict, key: str, where: str) -> float | None:
    return None if obj.get(key) is None else _num(obj, key, where)


def _coord(obj: dict, key: str, where: str, planar: bool = False) -> float:
    """WGS84 degrees, or for planar instances plain coordinates (lat = y, lng = x)."""
    value = _req(obj, key, where)
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise InputError(f"{where}: '{key}' must be a number")
    limit = 90 if key == "lat" else 180
    if not planar and not -limit <= value <= limit:
        raise InputError(f"{where}: '{key}' out of range")
    return float(value)


def _quantity(obj: dict, key: str, where: str) -> tuple[float, str]:
    q = _req(obj, key, where)
    if not isinstance(q, dict):
        raise InputError(f"{where}: '{key}' must be {{value, unit}}")
    unit = _req(q, "unit", f"{where}.{key}")
    return _num(q, "value", f"{where}.{key}"), str(unit)


def parse_clock(text: str, where: str) -> int:
    """'HH:MM' or 'HH:MM:SS' -> integer seconds from midnight."""
    try:
        parts = [int(p) for p in str(text).split(":")]
    except ValueError:
        raise InputError(f"{where}: invalid time '{text}'") from None
    if len(parts) not in (2, 3) or not (0 <= parts[0] <= 47 and 0 <= parts[1] < 60):
        raise InputError(f"{where}: invalid time '{text}'")
    seconds = parts[2] if len(parts) == 3 else 0
    if not 0 <= seconds < 60:
        raise InputError(f"{where}: invalid time '{text}'")
    return parts[0] * 3600 + parts[1] * 60 + seconds


def _opt_clock(obj: dict, key: str, where: str) -> int | None:
    return None if obj.get(key) is None else parse_clock(obj[key], f"{where}.{key}")


@dataclass(frozen=True)
class VehicleType:
    id: str
    capacity: float
    capacity_unit: str
    fixed_cost_vnd: float
    cost_per_km_vnd: float
    cost_per_hour_vnd: float
    max_distance_km: float | None
    max_duration_h: float | None
    emission_g_per_km: float
    emission_provenance: str
    skills: frozenset[str]


@dataclass(frozen=True)
class Depot:
    id: str
    lat: float
    lng: float


@dataclass(frozen=True)
class Vehicle:
    id: str
    type: VehicleType
    start_depot: Depot
    end_depot: Depot
    lez_skills: frozenset[str]
    shift_start_s: int | None
    shift_end_s: int | None


@dataclass(frozen=True)
class Order:
    id: str
    lat: float
    lng: float
    demand: float
    demand_unit: str
    tw_start_s: int | None
    tw_end_s: int | None
    service_s: float
    required_skills: frozenset[str]
    lez_skills: frozenset[str]


@dataclass(frozen=True)
class Params:
    carbon_price_vnd_per_t: float
    emission_model_id: str
    time_window_mode: str
    default_shift_start_s: int
    coordinates: str


@dataclass(frozen=True)
class Instance:
    instance_id: str
    sha256: str
    params: Params
    depots: dict[str, Depot]
    vehicle_types: dict[str, VehicleType]
    vehicles: dict[str, Vehicle]
    orders: dict[str, Order]


@dataclass(frozen=True)
class Leg:
    distance_m: float
    time_s: float


@dataclass(frozen=True)
class Route:
    vehicle_id: str
    start_s: int | None
    stops: tuple[str, ...]
    legs: tuple[Leg, ...]


@dataclass(frozen=True)
class RouteSet:
    meta: dict
    routes: tuple[Route, ...]
    unassigned: tuple[str, ...]
    reported: dict = field(default_factory=dict)
    # Declared by the producer when its legs are truncated (e.g. 0.1 for the Solomon one-decimal
    # convention); the leg sanity bound is relaxed by exactly this much. 0 = exact legs.
    leg_truncation_m: float = 0.0


def _skills(obj: dict, key: str) -> frozenset[str]:
    return frozenset(str(s) for s in (obj.get(key) or []))


def canonical_sha256(data: dict) -> str:
    """Hash of the bundle content (params, depots, vehicle types, vehicles, orders) without `meta`.

    Two bundles exported from different jobs therefore share a hash exactly when the solver input
    they describe is identical, which is what `compare` needs.
    """
    clone = {k: v for k, v in data.items() if k != "meta"}
    text = json.dumps(clone, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def parse_instance(data: dict) -> Instance:
    meta = _req(data, "meta", "instance")
    p = _req(data, "params", "instance")
    mode = str(_req(p, "time_window_mode", "params"))
    if mode not in TW_MODES:
        raise InputError(f"params.time_window_mode must be one of {TW_MODES}")
    coordinates = str(_req(p, "coordinates", "params"))
    if coordinates not in COORDINATE_SYSTEMS:
        raise InputError(f"params.coordinates must be one of {COORDINATE_SYSTEMS}")
    planar = coordinates == "planar"
    params = Params(
        carbon_price_vnd_per_t=_num(p, "carbon_price_vnd_per_t", "params"),
        emission_model_id=str(_req(p, "emission_model_id", "params")),
        time_window_mode=mode,
        default_shift_start_s=parse_clock(_req(p, "default_shift_start", "params"), "params.default_shift_start"),
        coordinates=coordinates,
    )

    types: dict[str, VehicleType] = {}
    for raw in _req(data, "vehicle_types", "instance"):
        where = f"vehicle_type {raw.get('id')}"
        cap, cap_unit = _quantity(raw, "capacity", where)
        ef, ef_unit = _quantity(raw, "emission_factor", where)
        if ef_unit != EMISSION_UNIT:
            raise InputError(f"{where}: emission_factor.unit must be '{EMISSION_UNIT}', got '{ef_unit}'")
        provenance = str(raw["emission_factor"].get("provenance") or "").strip()
        if not provenance:
            raise InputError(f"{where}: emission_factor.provenance is required")
        vt = VehicleType(
            id=str(_req(raw, "id", where)), capacity=cap, capacity_unit=cap_unit,
            fixed_cost_vnd=_num(raw, "fixed_cost_vnd", where),
            cost_per_km_vnd=_num(raw, "cost_per_km_vnd", where),
            cost_per_hour_vnd=_num(raw, "cost_per_hour_vnd", where),
            max_distance_km=_opt_num(raw, "max_distance_km", where),
            max_duration_h=_opt_num(raw, "max_duration_h", where),
            emission_g_per_km=ef, emission_provenance=provenance,
            skills=_skills(raw, "skills"),
        )
        if vt.id in types:
            raise InputError(f"duplicate vehicle_type id {vt.id}")
        types[vt.id] = vt

    depots: dict[str, Depot] = {}
    for raw in _req(data, "depots", "instance"):
        where = f"depot {raw.get('id')}"
        d = Depot(id=str(_req(raw, "id", where)), lat=_coord(raw, "lat", where, planar),
                  lng=_coord(raw, "lng", where, planar))
        if d.id in depots:
            raise InputError(f"duplicate depot id {d.id}")
        depots[d.id] = d

    vehicles: dict[str, Vehicle] = {}
    for raw in _req(data, "vehicles", "instance"):
        where = f"vehicle {raw.get('id')}"
        type_id = str(_req(raw, "vehicle_type_id", where))
        if type_id not in types:
            raise InputError(f"{where}: unknown vehicle_type_id {type_id}")
        ends = []
        for key in ("start_depot_id", "end_depot_id"):
            depot_id = str(_req(raw, key, where))
            if depot_id not in depots:
                raise InputError(f"{where}: unknown {key} {depot_id}")
            ends.append(depots[depot_id])
        v = Vehicle(
            id=str(_req(raw, "id", where)), type=types[type_id],
            start_depot=ends[0], end_depot=ends[1],
            lez_skills=_skills(raw, "lez_skills"),
            shift_start_s=_opt_clock(raw, "shift_start", where),
            shift_end_s=_opt_clock(raw, "shift_end", where),
        )
        if v.id in vehicles:
            raise InputError(f"duplicate vehicle id {v.id}")
        vehicles[v.id] = v

    orders: dict[str, Order] = {}
    for raw in _req(data, "orders", "instance"):
        where = f"order {raw.get('id')}"
        demand, unit = _quantity(raw, "demand", where)
        o = Order(
            id=str(_req(raw, "id", where)),
            lat=_coord(raw, "lat", where, planar), lng=_coord(raw, "lng", where, planar),
            demand=demand, demand_unit=unit,
            tw_start_s=_opt_clock(raw, "tw_start", where),
            tw_end_s=_opt_clock(raw, "tw_end", where),
            service_s=_num(raw, "service_time_s", where),
            required_skills=_skills(raw, "required_skills"),
            lez_skills=_skills(raw, "lez_skills"),
        )
        if o.id in orders:
            raise InputError(f"duplicate order id {o.id}")
        orders[o.id] = o

    return Instance(
        instance_id=str(_req(meta, "instance_id", "meta")),
        sha256=canonical_sha256(data), params=params, depots=depots,
        vehicle_types=types, vehicles=vehicles, orders=orders,
    )


def parse_route_set(data: dict) -> RouteSet:
    units = _req(data, "leg_units", "route set")
    d_unit, t_unit = str(_req(units, "distance", "leg_units")), str(_req(units, "time", "leg_units"))
    if d_unit not in DISTANCE_TO_M or t_unit not in TIME_TO_S:
        raise InputError(f"leg_units must be distance in {list(DISTANCE_TO_M)} and time in {list(TIME_TO_S)}")
    d_factor, t_factor = DISTANCE_TO_M[d_unit], TIME_TO_S[t_unit]
    truncation = (_opt_num(units, "distance_truncation", "leg_units") or 0.0) * d_factor

    routes = []
    for i, raw in enumerate(_req(data, "routes", "route set")):
        where = f"route[{i}]"
        stops = tuple(str(s) for s in _req(raw, "stops", where))
        if not stops:
            raise InputError(f"{where}: empty route; omit unused vehicles")
        legs_raw = _req(raw, "legs", where)
        if len(legs_raw) != len(stops) + 1:
            raise InputError(f"{where}: expected {len(stops) + 1} legs (depot -> stops -> depot), got {len(legs_raw)}")
        legs = tuple(
            Leg(_num(leg, "distance", f"{where}.legs[{j}]") * d_factor,
                _num(leg, "time", f"{where}.legs[{j}]") * t_factor)
            for j, leg in enumerate(legs_raw)
        )
        routes.append(Route(
            vehicle_id=str(_req(raw, "vehicle_id", where)),
            start_s=_opt_clock(raw, "start_time", where),
            stops=stops, legs=legs,
        ))
    return RouteSet(
        meta=dict(data.get("meta") or {}), routes=tuple(routes),
        unassigned=tuple(str(o) for o in (data.get("unassigned") or [])),
        reported=dict(data.get("reported") or {}),
        leg_truncation_m=truncation,
    )


def load_json(path: str | Path) -> dict:
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)
