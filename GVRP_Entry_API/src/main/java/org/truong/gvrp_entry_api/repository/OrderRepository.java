package org.truong.gvrp_entry_api.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.enums.OrderStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Find all orders by branch ID
     * @param orderId Order ID
     * @param branchId Branch ID
     * @return List of orders
     */
    Optional<Order> findByIdAndBranchId(Long orderId, Long branchId);

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

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Order e SET e.status = :status WHERE e.id IN :ids")
    void updateStatusByIds(
            @Param("ids") List<Long> orderIds,
            @Param("status") OrderStatus status
    );
}
