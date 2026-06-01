package org.truong.gvrp_entry_api.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.truong.gvrp_entry_api.entity.Vehicle;

import java.util.List;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    /**
     * Find all vehicles by fleet ID
     * @param branchId Fleet ID
     * @return List of vehicles
     */
    Page<Vehicle> findByFleetBranchId(Long branchId, Pageable pageable);

    /**
     * Check if license plate exists
     * @param vehicleLicensePlate License plate
     * @return true if exists
     */
    boolean existsByVehicleLicensePlate(String vehicleLicensePlate);

    @EntityGraph(attributePaths = {"startDepot", "endDepot", "fleet", "vehicleType"})
    @Query("""
        SELECT v FROM Vehicle v 
        WHERE v.id IN :ids 
        ORDER BY v.id ASC
    """)
    List<Vehicle> findAllByIdWithDepots(@Param("ids") List<Long> ids);


}
