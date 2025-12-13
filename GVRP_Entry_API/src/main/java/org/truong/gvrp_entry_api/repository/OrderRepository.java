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
     * @param ids list of Order IDs
     * @param status Order status
     * @return List of orders
     */
    @Query("""
    SELECT o FROM Order o
    WHERE o.id IN :ids 
    AND o.status = :status
    ORDER BY o.priority ASC, o.id ASC
    """)
    List<Order> findByStatus(@Param("ids") List<Long> ids,@Param("status") OrderStatus status);

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


    Page<Order> findByBranchIdOrderByCreatedAtDesc(Long branchId, Pageable pageable);
}
