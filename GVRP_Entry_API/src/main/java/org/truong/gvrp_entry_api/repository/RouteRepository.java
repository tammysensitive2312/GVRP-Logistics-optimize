package org.truong.gvrp_entry_api.repository;

import org.truong.gvrp_entry_api.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RouteRepository extends JpaRepository<Route, Long> {

    /**
     * Find all routes by solution ID
     * @param solutionId Solution ID
     * @return List of routes ordered by routeOrder
     */
    List<Route> findBySolutionIdOrderByRouteOrderAsc(Long solutionId);

    /**
     * Find routes by vehicle ID
     * @param vehicleId Vehicle ID
     * @return List of routes
     */
    List<Route> findByVehicleId(Long vehicleId);

    /**
     * Find routes by solution ID and vehicle ID
     * @param solutionId Solution ID
     * @param vehicleId Vehicle ID
     * @return List of routes
     */
    List<Route> findBySolutionIdAndVehicleId(Long solutionId, Long vehicleId);

    /**
     * Calculate total distance for solution
     * @param solutionId Solution ID
     * @return Total distance
     */
    @Query("""
        SELECT COALESCE(SUM(r.distance), 0.0)
        FROM Route r
        WHERE r.solution.id = :solutionId
        """)
    Double calculateTotalDistance(@Param("solutionId") Long solutionId);

    /**
     * Find routes with low utilization
     * @param solutionId Solution ID
     * @param threshold Utilization threshold (e.g., 60.0)
     * @return List of under-utilized routes
     */
    @Query("""
        SELECT r FROM Route r
        WHERE r.solution.id = :solutionId
        AND r.loadUtilization < :threshold
        ORDER BY r.loadUtilization ASC
        """)
    List<Route> findUnderUtilizedRoutes(
            @Param("solutionId") Long solutionId,
            @Param("threshold") Double threshold
    );

    /**
     * Count routes in solution
     * @param solutionId Solution ID
     * @return Count
     */
    Long countBySolutionId(Long solutionId);
}
