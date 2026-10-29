package org.truong.gvrp_entry_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.truong.gvrp_entry_api.entity.VehiclePermit;

import java.util.List;
import java.util.Optional;

/** Branch-owned permits; every lookup is scoped to a branch (branch_id is NOT NULL). */
public interface VehiclePermitRepository extends JpaRepository<VehiclePermit, Long> {

    List<VehiclePermit> findByVehicleIdAndBranchIdOrderByIdAsc(Long vehicleId, Long branchId);

    Optional<VehiclePermit> findByIdAndVehicleIdAndBranchId(Long id, Long vehicleId, Long branchId);

    long countByVehicleIdAndBranchId(Long vehicleId, Long branchId);

    boolean existsByBranchZoneIdAndBranchId(Long branchZoneId, Long branchId);

    List<VehiclePermit> findByVehicleIdAndBranchZoneIdAndBranchId(Long vehicleId, Long branchZoneId, Long branchId);
}
