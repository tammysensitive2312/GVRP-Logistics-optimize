package org.truong.gvrp_entry_api.entity.enums;

/**
 * Legal vehicle class used by zone rules (LEZ-001). Set explicitly by the
 * planner; {@link #suggestFor(VehicleCategory)} only proposes a default.
 */
public enum LegalClass {
    MOTORBIKE,
    CAR,
    TRUCK,
    BUS,
    NON_MOTORIZED;

    /** Default suggestion from the vehicle category (design §3.4); never applied silently. */
    public static LegalClass suggestFor(VehicleCategory category) {
        if (category == null) {
            return null;
        }
        return switch (category) {
            case BICYCLE -> NON_MOTORIZED;
            case MOTORCYCLE -> MOTORBIKE;
            case CAR -> CAR;
            case VAN, PICKUP_TRUCK, BOX_TRUCK, FLATBED_TRUCK, TANKER_TRUCK, SEMI_TRUCK -> TRUCK;
        };
    }
}
