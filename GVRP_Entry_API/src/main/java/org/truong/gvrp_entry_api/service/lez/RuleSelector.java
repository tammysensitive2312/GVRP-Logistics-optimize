package org.truong.gvrp_entry_api.service.lez;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.truong.gvrp_entry_api.entity.enums.EnergyType;
import org.truong.gvrp_entry_api.entity.enums.LegalClass;

import java.util.List;

/**
 * Vehicle selector of a zone rule (design §3.1). Every present field must
 * match (AND); an absent field does not constrain.
 *
 * <p>Property names are explicit so the stored JSON and the wire format stay
 * camelCase whatever naming strategy the surrounding mapper uses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuleSelector(
        @JsonProperty("legalClasses") List<LegalClass> legalClasses,
        @JsonProperty("energyTypes") List<EnergyType> energyTypes,
        @JsonProperty("weightMeasure") WeightMeasure weightMeasure,
        @JsonProperty("weightKg") WeightBounds weightKg,
        @JsonProperty("emissionStandardBelow") Integer emissionStandardBelow,
        @JsonProperty("appBased") Boolean appBased) {

    /** Which vehicle figure a weight bound reads. */
    public enum WeightMeasure {
        /** {@code VehicleType.grossWeightKg}, the legal gross design weight. */
        GROSS,
        /** {@code VehicleType.capacity}, read as kilograms of payload. */
        PAYLOAD
    }

    /** Weight bounds in kilograms; every present bound must hold. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WeightBounds(
            @JsonProperty("gt") Integer gt,
            @JsonProperty("gte") Integer gte,
            @JsonProperty("lt") Integer lt,
            @JsonProperty("lte") Integer lte) {

        boolean isEmpty() {
            return gt == null && gte == null && lt == null && lte == null;
        }

        boolean holds(int value) {
            return (gt == null || value > gt)
                    && (gte == null || value >= gte)
                    && (lt == null || value < lt)
                    && (lte == null || value <= lte);
        }
    }
}
