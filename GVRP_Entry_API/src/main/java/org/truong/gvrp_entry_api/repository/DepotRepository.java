package org.truong.gvrp_entry_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.truong.gvrp_entry_api.entity.Depot;

import java.util.List;
import java.util.Optional;

public interface DepotRepository extends JpaRepository<Depot, Long> {

    /**
     * Find all depots by branch ID
     * @param branchId Branch ID
     * @return List of depots
     */
    List<Depot> findByBranchId(Long branchId);

    /**
     * Find depot by branch ID and name
     * @param branchId Branch ID
     * @param name Depot name
     * @return Optional Depot
     */
    Optional<Depot> findByBranchIdAndName(Long branchId, String name);

    /**
     * Check if branch has any depots
     * @param branchId Branch ID
     * @return true if exists
     */
    boolean existsByBranchId(Long branchId);

    /**
     * Find depots within radius (using spatial query)
     * @param branchId Branch ID
     * @param longitude Longitude
     * @param latitude Latitude
     * @param radiusKm Radius in kilometers
     * @return List of depots
     */
    @Query(value = """
        SELECT d.* FROM depots d
        WHERE d.branch_id = :branchId
        AND ST_Distance_Sphere(
            d.location,
            POINT(:longitude, :latitude)
        ) <= :radiusKm * 1000
        """, nativeQuery = true)
    List<Depot> findDepotsWithinRadius(
            @Param("branchId") Long branchId,
            @Param("longitude") Double longitude,
            @Param("latitude") Double latitude,
            @Param("radiusKm") Double radiusKm
    );
}
