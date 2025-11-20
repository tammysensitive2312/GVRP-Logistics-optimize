package org.truong.gvrp_entry_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.truong.gvrp_entry_api.entity.Fleet;

import java.util.Optional;

public interface FleetRepository extends JpaRepository<Fleet, Long> {

    /**
     * Find fleet by branch ID
     * @param branchId Branch ID
     * @return Optional Fleet
     */
    Optional<Fleet> findByBranchId(Long branchId);

    /**
     * Check if branch has fleet
     * @param branchId Branch ID
     * @return true if exists
     */
    boolean existsByBranchId(Long branchId);

    /**
     * Find fleet by branch ID and fleet name
     * @param branchId Branch ID
     * @param fleetName Fleet name
     * @return Optional Fleet
     */
    Optional<Fleet> findByBranchIdAndFleetName(Long branchId, String fleetName);
}
