package org.truong.gvrp_entry_api.repository;

import org.truong.gvrp_entry_api.entity.RouteSegment;
import org.truong.gvrp_entry_api.entity.enums.LocationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
public interface RouteSegmentRepository extends JpaRepository<RouteSegment, Long> {

    /**
     * Find all segments by route ID, ordered by sequence
     * @param routeId Route ID
     * @return List of route segments
     */
    List<RouteSegment> findByRouteIdOrderBySequenceNumberAsc(Long routeId);

    /**
     * Find segments by order ID
     * @param orderId Order ID
     * @return List of route segments
     */
    List<RouteSegment> findByOrderId(Long orderId);

    /**
     * Find segments by route ID and location type
     * @param routeId Route ID
     * @param locationType Location type (DEPOT or ORDER)
     * @return List of route segments
     */
    @Query("""
        SELECT rs FROM RouteSegment rs
        WHERE rs.route.id = :routeId
        AND (rs.fromType = :locationType OR rs.toType = :locationType)
        ORDER BY rs.sequenceNumber ASC
        """)
    List<RouteSegment> findByRouteIdAndLocationType(
            @Param("routeId") Long routeId,
            @Param("locationType") LocationType locationType
    );

    /**
     * Calculate total distance for route
     * @param routeId Route ID
     * @return Total distance
     */
    @Query("""
        SELECT COALESCE(SUM(rs.distance), 0.0)
        FROM RouteSegment rs
        WHERE rs.route.id = :routeId
        """)
    Double calculateTotalDistanceForRoute(@Param("routeId") Long routeId);

    /**
     * Count segments in route
     * @param routeId Route ID
     * @return Count
     */
    Long countByRouteId(Long routeId);

    /**
     * Find first segment of route (departure from depot)
     * @param routeId Route ID
     * @return RouteSegment or null
     */
    @Query("""
        SELECT rs FROM RouteSegment rs
        WHERE rs.route.id = :routeId
        AND rs.sequenceNumber = 1
        """)
    RouteSegment findFirstSegmentOfRoute(@Param("routeId") Long routeId);

    /**
     * Find last segment of route (return to depot)
     * @param routeId Route ID
     * @return RouteSegment or null
     */
    @Query("""
        SELECT rs FROM RouteSegment rs
        WHERE rs.route.id = :routeId
        ORDER BY rs.sequenceNumber DESC
        LIMIT 1
        """)
    RouteSegment findLastSegmentOfRoute(@Param("routeId") Long routeId);
}
