package org.truong.gvrp_entry_api.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.truong.gvrp_entry_api.dto.request.VehicleFeaturesDTO;
import org.truong.gvrp_entry_api.dto.request.VehicleTypeInputDTO;
import org.truong.gvrp_entry_api.dto.response.VehicleTypeDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.VehicleType;
import org.truong.gvrp_entry_api.entity.enums.EnergyType;
import org.truong.gvrp_entry_api.entity.enums.LegalClass;
import org.truong.gvrp_entry_api.entity.enums.VehicleCategory;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.VehicleTypeRepository;
import org.truong.gvrp_entry_api.service.VehicleFeaturesService;
import org.truong.gvrp_entry_api.service.VehicleTypeService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Legal attributes on the vehicle-type API (LEZ-001 P1a, VT-T1). Uses the
 * MapStruct-generated mapper without a Spring context.
 */
@ExtendWith(MockitoExtension.class)
class VehicleTypeLegalAttributesTest {

    @Mock
    private VehicleTypeRepository typeRepository;
    @Mock
    private BranchRepository branchRepository;

    private VehicleFeaturesService featuresService;
    private VehicleTypeMapper mapper;
    private VehicleTypeService service;

    @BeforeEach
    void setUp() {
        featuresService = new VehicleFeaturesService(new ObjectMapper());
        VehicleTypeMapperImpl impl = new VehicleTypeMapperImpl();
        ReflectionTestUtils.setField(impl, "vehicleFeaturesService", featuresService);
        mapper = impl;
        service = new VehicleTypeService(typeRepository, mapper, featuresService, branchRepository);
    }

    private static VehicleFeaturesDTO boxTruck() {
        return VehicleFeaturesDTO.builder().category(VehicleCategory.BOX_TRUCK).emissionFactor(0.2).build();
    }

    private static VehicleTypeInputDTO input() {
        VehicleTypeInputDTO input = new VehicleTypeInputDTO();
        input.setTypeName("Test truck");
        input.setCapacity(1500);
        input.setFeatures(boxTruck());
        return input;
    }

    @Test
    void vtT1_fourAttributesRoundTrip() {
        VehicleTypeInputDTO input = input();
        input.setLegalClass(LegalClass.TRUCK);
        input.setGrossWeightKg(4000);
        input.setEnergyType(EnergyType.DIESEL);
        input.setEmissionStandard(5);

        VehicleType entity = mapper.toEntity(input);
        entity.setVehicleFeatures(featuresService.toJson(input.getFeatures()));
        VehicleTypeDTO dto = mapper.toDTO(entity);

        assertThat(entity.getLegalClass()).isEqualTo(LegalClass.TRUCK);
        assertThat(entity.getGrossWeightKg()).isEqualTo(4000);
        assertThat(entity.getEnergyType()).isEqualTo(EnergyType.DIESEL);
        assertThat(entity.getEmissionStandard()).isEqualTo(5);
        assertThat(entity.getCapacity()).isEqualTo(1500);
        assertThat(dto.getLegalClass()).isEqualTo(LegalClass.TRUCK);
        assertThat(dto.getGrossWeightKg()).isEqualTo(4000);
        assertThat(dto.getEnergyType()).isEqualTo(EnergyType.DIESEL);
        assertThat(dto.getEmissionStandard()).isEqualTo(5);
    }

    @Test
    void vtT1_boxTruckSuggestsTruck_whileLegalClassStaysNull() {
        VehicleType entity = mapper.toEntity(input());
        entity.setVehicleFeatures(featuresService.toJson(boxTruck()));

        VehicleTypeDTO dto = mapper.toDTO(entity);

        assertThat(dto.getSuggestedLegalClass()).isEqualTo(LegalClass.TRUCK);
        assertThat(dto.getLegalClass()).isNull();
        assertThat(entity.getLegalClass()).isNull();
    }

    @Test
    void suggestionTable_followsDesign34() {
        assertThat(LegalClass.suggestFor(VehicleCategory.BICYCLE)).isEqualTo(LegalClass.NON_MOTORIZED);
        assertThat(LegalClass.suggestFor(VehicleCategory.MOTORCYCLE)).isEqualTo(LegalClass.MOTORBIKE);
        assertThat(LegalClass.suggestFor(VehicleCategory.CAR)).isEqualTo(LegalClass.CAR);
        for (VehicleCategory truck : new VehicleCategory[]{VehicleCategory.VAN, VehicleCategory.PICKUP_TRUCK,
                VehicleCategory.BOX_TRUCK, VehicleCategory.FLATBED_TRUCK, VehicleCategory.TANKER_TRUCK,
                VehicleCategory.SEMI_TRUCK}) {
            assertThat(LegalClass.suggestFor(truck)).isEqualTo(LegalClass.TRUCK);
        }
        assertThat(LegalClass.suggestFor(null)).isNull();
    }

    @Test
    void update_writesTheFourAttributes() {
        VehicleType existing = VehicleType.builder().id(4L).typeName("Old").capacity(1000)
                .vehicleFeatures(featuresService.toJson(boxTruck())).build();
        when(typeRepository.findByIdAndBranchId(4L, 2L)).thenReturn(Optional.of(existing));
        when(typeRepository.save(any(VehicleType.class))).thenAnswer(inv -> inv.getArgument(0));
        VehicleTypeInputDTO input = input();
        input.setLegalClass(LegalClass.TRUCK);
        input.setGrossWeightKg(3000);
        input.setEnergyType(EnergyType.ELECTRIC);
        input.setEmissionStandard(0);

        VehicleTypeDTO dto = service.updateVehicleType(4L, input, 2L);

        assertThat(existing.getGrossWeightKg()).isEqualTo(3000);
        assertThat(existing.getEnergyType()).isEqualTo(EnergyType.ELECTRIC);
        assertThat(dto.getLegalClass()).isEqualTo(LegalClass.TRUCK);
        assertThat(dto.getEmissionStandard()).isZero();
    }

    @Test
    void negativeLegalNumbers_areRejected() {
        when(branchRepository.findById(2L)).thenReturn(Optional.of(Branch.builder().id(2L).build()));
        VehicleTypeInputDTO input = input();
        input.setGrossWeightKg(-1);

        assertThatThrownBy(() -> service.createVehicleType(input, 2L)).isInstanceOf(DataInvalidException.class);
        verify(typeRepository, never()).save(any());
    }
}
