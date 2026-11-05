"""Versioned emission models (DESIGN.md §6). Units: distance km, factor g CO2/km, result kg CO2."""

from __future__ import annotations

from .model import InputError, VehicleType

EM0 = "EM0-distance-v1"
MODELS = (EM0,)


def route_co2_kg(model_id: str, distance_km: float, vehicle_type: VehicleType) -> float:
    if model_id == EM0:
        # Tank-to-wheel, distance-based: D [km] * e [g/km] / 1000 [g/kg].
        return distance_km * vehicle_type.emission_g_per_km / 1000.0
    raise InputError(f"unknown emission_model_id '{model_id}'; known: {MODELS}")
