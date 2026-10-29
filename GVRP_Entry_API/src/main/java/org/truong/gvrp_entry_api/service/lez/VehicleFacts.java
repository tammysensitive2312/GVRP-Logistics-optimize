package org.truong.gvrp_entry_api.service.lez;

import org.truong.gvrp_entry_api.entity.VehicleType;
import org.truong.gvrp_entry_api.entity.enums.EnergyType;
import org.truong.gvrp_entry_api.entity.enums.LegalClass;

/**
 * Legal facts of one vehicle that rule selectors read. Any field may be null
 * (unknown); a selector that needs an unknown fact treats it as a match.
 *
 * @param grossWeightKg gross design weight in kilograms
 * @param capacity      {@code VehicleType.capacity}, read by {@code PAYLOAD} rules as kilograms
 */
public record VehicleFacts(
        LegalClass legalClass,
        EnergyType energyType,
        Integer grossWeightKg,
        Integer capacity,
        Integer emissionStandard) {

    public static VehicleFacts of(VehicleType type) {
        if (type == null) {
            return new VehicleFacts(null, null, null, null, null);
        }
        return new VehicleFacts(type.getLegalClass(), type.getEnergyType(), type.getGrossWeightKg(),
                type.getCapacity(), type.getEmissionStandard());
    }
}
