package org.truong.gvrp_entry_api.service.lez;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truong.gvrp_entry_api.dto.request.VehiclePermitInputDTO;
import org.truong.gvrp_entry_api.dto.response.VehiclePermitDTO;
import org.truong.gvrp_entry_api.entity.BranchZone;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.VehiclePermit;
import org.truong.gvrp_entry_api.repository.BranchZoneRepository;
import org.truong.gvrp_entry_api.repository.VehiclePermitRepository;
import org.truong.gvrp_entry_api.repository.VehicleRepository;
import org.truong.gvrp_entry_api.util.AppConstant;

import java.util.List;

/**
 * Vehicle permits (LEZ-R05). The vehicle and the zone must both belong to the
 * caller's branch; otherwise 404, before anything is written (LEZ-R14).
 */
@Service
@RequiredArgsConstructor
public class VehiclePermitService {

    public static final int MAX_PERMITS_PER_VEHICLE = 50;
    private static final int MAX_PERMIT_NO_LENGTH = 100;

    private final VehiclePermitRepository permitRepository;
    private final VehicleRepository vehicleRepository;
    private final BranchZoneRepository zoneRepository;

    @Transactional(readOnly = true)
    public List<VehiclePermitDTO> listPermits(Long vehicleId, Long branchId) {
        Vehicle vehicle = findVehicle(vehicleId, branchId);
        return permitRepository.findByVehicleIdAndBranchIdOrderByIdAsc(vehicle.getId(), branchId).stream()
                .map(LezSupport::toPermitDTO)
                .toList();
    }

    @Transactional
    public VehiclePermitDTO createPermit(Long vehicleId, VehiclePermitInputDTO input, Long branchId) {
        Vehicle vehicle = findVehicle(vehicleId, branchId);
        BranchZone zone = findZone(input, branchId);
        RuleSchedule schedule = validate(input);
        if (permitRepository.countByVehicleIdAndBranchId(vehicle.getId(), branchId) >= MAX_PERMITS_PER_VEHICLE) {
            throw LezSupport.invalid("A vehicle may have at most " + MAX_PERMITS_PER_VEHICLE + " permits");
        }
        VehiclePermit permit = VehiclePermit.builder()
                .branch(zone.getBranch())
                .vehicle(vehicle)
                .branchZone(zone)
                .build();
        apply(permit, input, schedule);
        return LezSupport.toPermitDTO(permitRepository.save(permit));
    }

    @Transactional
    public VehiclePermitDTO updatePermit(Long vehicleId, Long permitId, VehiclePermitInputDTO input, Long branchId) {
        Vehicle vehicle = findVehicle(vehicleId, branchId);
        VehiclePermit permit = findPermit(permitId, vehicle, branchId);
        BranchZone zone = findZone(input, branchId);
        RuleSchedule schedule = validate(input);
        permit.setBranchZone(zone);
        apply(permit, input, schedule);
        return LezSupport.toPermitDTO(permitRepository.save(permit));
    }

    @Transactional
    public void deletePermit(Long vehicleId, Long permitId, Long branchId) {
        Vehicle vehicle = findVehicle(vehicleId, branchId);
        permitRepository.delete(findPermit(permitId, vehicle, branchId));
    }

    // ---------- helpers ----------

    private Vehicle findVehicle(Long vehicleId, Long branchId) {
        LezSupport.requireBranch(branchId);
        return vehicleRepository.findByIdAndFleetBranchId(vehicleId, branchId)
                .orElseThrow(() -> new LezNotFoundException(AppConstant.VEHICLE));
    }

    private VehiclePermit findPermit(Long permitId, Vehicle vehicle, Long branchId) {
        return permitRepository.findByIdAndVehicleIdAndBranchId(permitId, vehicle.getId(), branchId)
                .orElseThrow(() -> new LezNotFoundException(AppConstant.VEHICLE_PERMIT));
    }

    private BranchZone findZone(VehiclePermitInputDTO input, Long branchId) {
        if (input == null || input.getBranchZoneId() == null) {
            throw LezSupport.invalid("branchZoneId is required");
        }
        return zoneRepository.findByIdAndBranchId(input.getBranchZoneId(), branchId)
                .orElseThrow(() -> new LezNotFoundException(AppConstant.BRANCH_ZONE));
    }

    private static RuleSchedule validate(VehiclePermitInputDTO input) {
        if (input.getValidFrom() == null || input.getValidTo() == null) {
            throw LezSupport.invalid("validFrom and validTo are required");
        }
        if (input.getValidFrom().isAfter(input.getValidTo())) {
            throw LezSupport.invalid("validFrom must not be after validTo");
        }
        if (input.getPermitNo() != null && input.getPermitNo().length() > MAX_PERMIT_NO_LENGTH) {
            throw LezSupport.invalid("permitNo is longer than " + MAX_PERMIT_NO_LENGTH + " characters");
        }
        return LezSupport.validated(() -> RuleValidator.parsePermitSchedule(input.getSchedule()));
    }

    private static void apply(VehiclePermit permit, VehiclePermitInputDTO input, RuleSchedule schedule) {
        permit.setPermitNo(input.getPermitNo());
        permit.setSchedule(schedule);
        permit.setValidFrom(input.getValidFrom());
        permit.setValidTo(input.getValidTo());
        permit.setNote(input.getNote());
    }
}
