package org.truong.gvrp_entry_api.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.enums.VehicleStatus;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    /**
     * Find all vehicles by fleet ID
     * @param branchId Fleet ID
     * @return List of vehicles
     */
    Page<Vehicle> findByFleetBranchId(Long branchId, Pageable pageable);

    /**
     * Find vehicles by status
     * @param status Vehicle status
     * @return List of vehicles
     */
    List<Vehicle> findByStatus(VehicleStatus status);

    /**
     * Find vehicle by license plate
     * @param vehicleLicensePlate License plate
     * @return Optional Vehicle
     */
    Optional<Vehicle> findByVehicleLicensePlate(String vehicleLicensePlate);

    /**
     * Check if license plate exists
     * @param vehicleLicensePlate License plate
     * @return true if exists
     */
    boolean existsByVehicleLicensePlate(String vehicleLicensePlate);

    /**
     * Find available vehicles by branch ID
     * @param branchId Branch ID
     * @param status Vehicle status (AVAILABLE)
     * @return List of available vehicles
     */
    @Query("""
        SELECT v FROM Vehicle v
        JOIN v.fleet f
        WHERE f.branch.id = :branchId
        AND v.status = :status
        ORDER BY v.capacity DESC
        """)
    List<Vehicle> findAvailableVehiclesByBranch(
            @Param("branchId") Long branchId,
            @Param("status") VehicleStatus status
    );

    /**
     * Find vehicles by branch ID and status
     * @param branchId Branch ID
     * @param status Vehicle status
     * @return List of vehicles
     */
    @Query("""
        SELECT v FROM Vehicle v
        JOIN v.fleet f
        WHERE f.branch.id = :branchId
        AND v.status = :status
        """)
    List<Vehicle> findByBranchIdAndStatus(
            @Param("branchId") Long branchId,
            @Param("status") VehicleStatus status
    );

    /**
     * Count available vehicles in branch
     * @param branchId Branch ID
     * @return Count of available vehicles
     */
    @Query("""
        SELECT COUNT(v) FROM Vehicle v
        JOIN v.fleet f
        WHERE f.branch.id = :branchId
        AND v.status = 'AVAILABLE'
        """)
    Long countAvailableVehiclesByBranch(@Param("branchId") Long branchId);
}
