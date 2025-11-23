package org.truong.gvrp_entry_api.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.enums.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Find all orders by branch ID
     * @param orderId Order ID
     * @param branchId Branch ID
     * @return List of orders
     */
    Optional<Order> findByIdAndBranchId(Long orderId, Long branchId);

    /**
     * Find orders by branch ID and status
     * @param branchId Branch ID
     * @param status Order status
     * @return List of orders
     */
    List<Order> findByBranchIdAndStatus(Long branchId, OrderStatus status);

    /**
     * Find order by branch ID and order code
     * @param branchId Branch ID
     * @param orderCode Order code
     * @return Optional Order
     */
    Optional<Order> findByBranchIdAndOrderCode(Long branchId, String orderCode);

    /**
     * Check if order code exists in branch
     * @param branchId Branch ID
     * @param orderCode Order code
     * @return true if exists
     */
    boolean existsByBranchIdAndOrderCode(Long branchId, String orderCode);

    /**
     * Find orders within radius (spatial query)
     * @param branchId Branch ID
     * @param longitude Center longitude
     * @param latitude Center latitude
     * @param radiusKm Radius in kilometers
     * @return List of orders
     */
    @Query(value = """
        SELECT o.* FROM orders o
        WHERE o.branch_id = :branchId
        AND ST_Distance_Sphere(
            o.location,
            POINT(:longitude, :latitude)
        ) <= :radiusKm * 1000
        ORDER BY ST_Distance_Sphere(o.location, POINT(:longitude, :latitude))
        """, nativeQuery = true)
    List<Order> findOrdersWithinRadius(
            @Param("branchId") Long branchId,
            @Param("longitude") Double longitude,
            @Param("latitude") Double latitude,
            @Param("radiusKm") Double radiusKm
    );

    /**
     * Find orders by multiple statuses
     * @param branchId Branch ID
     * @param statuses List of statuses
     * @return List of orders
     */
    List<Order> findByBranchIdAndStatusIn(Long branchId, List<OrderStatus> statuses);

    /**
     * Find high priority orders
     * @param branchId Branch ID
     * @param maxPriority Maximum priority value (lower = higher priority)
     * @return List of orders
     */
    @Query("""
        SELECT o FROM Order o
        WHERE o.branch.id = :branchId
        AND o.priority IS NOT NULL
        AND o.priority <= :maxPriority
        AND o.status = 'SCHEDULED'
        ORDER BY o.priority ASC
        """)
    List<Order> findHighPriorityOrders(
            @Param("branchId") Long branchId,
            @Param("maxPriority") Integer maxPriority
    );

    /**
     * Find scheduled orders created within date range
     * @param branchId Branch ID
     * @param startDate Start date
     * @param endDate End date
     * @return List of orders
     */
    @Query("""
        SELECT o FROM Order o
        WHERE o.branch.id = :branchId
        AND o.status = 'SCHEDULED'
        AND o.createdAt BETWEEN :startDate AND :endDate
        ORDER BY o.createdAt DESC
        """)
    List<Order> findScheduledOrdersByDateRange(
            @Param("branchId") Long branchId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Count orders by status
     * @param branchId Branch ID
     * @param status Order status
     * @return Count
     */
    Long countByBranchIdAndStatus(Long branchId, OrderStatus status);

    Page<Order> findByBranchIdOrderByCreatedAtDesc(Long branchId, Pageable pageable);
}
