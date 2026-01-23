package org.truong.gvrp_entry_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.truong.gvrp_entry_api.entity.VehicleType;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleTypeRepository extends JpaRepository<VehicleType, Long> {
    List<VehicleType> findAllByBranchId(Long branchId);

    Optional<VehicleType> findByIdAndBranchId(Long id, Long branchId);
}