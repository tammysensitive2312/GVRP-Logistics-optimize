package org.truong.gvrp_entry_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.truong.gvrp_entry_api.entity.Fleet;

import java.util.List;
import java.util.Optional;

public interface FleetRepository extends JpaRepository<Fleet, Long> {

    /**
     * Find fleet by fleet ID and branch ID
     * @param branchId Branch ID
     * @param branchId Branch ID
     * @return Optional Fleet
     */
    Optional<Fleet> findByIdAndBranchId(Long fleetId, Long branchId);

    /**
     * Check if branch has fleet
     * @param branchId Branch ID
     * @return true if exists
     */
    boolean existsByBranchId(Long branchId);

    /**
     * Find fleet by branch ID
     * @param branchId Branch ID
     * @return List of Fleets
     */
    List<Fleet> findAllByBranchId(Long branchId);
}
