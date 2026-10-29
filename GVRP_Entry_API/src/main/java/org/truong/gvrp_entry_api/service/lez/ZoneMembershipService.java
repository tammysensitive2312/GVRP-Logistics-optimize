package org.truong.gvrp_entry_api.service.lez;

import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truong.gvrp_entry_api.dto.response.OrderZoneMembershipDTO;
import org.truong.gvrp_entry_api.entity.BranchZone;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.enums.BranchZoneMode;
import org.truong.gvrp_entry_api.repository.BranchZoneRepository;
import org.truong.gvrp_entry_api.repository.OrderRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Read-only "which enabled low-emission zones does this order fall in?" for the
 * order table badge (LEZ-001 P1c, design-p1c §2.1, deviation from design §7).
 *
 * <p>Lives in the {@code lez} package so it can reuse the package-private
 * {@link ZoneRuleLoader#effectiveGeometry} and {@link GeoJsonGeometryParser#covers}
 * the compiler uses: membership and the compiler must agree on both geometry and
 * boundary. Nothing is written and no coordinate or customer field is logged.
 *
 * <p>Another branch's order is omitted exactly like a missing one, so the
 * endpoint leaks nothing about ids the caller does not own.
 */
@Service
@RequiredArgsConstructor
public class ZoneMembershipService {

    /** Upper bound of ids in one request (design-p1c §2.1). */
    static final int MAX_ORDERS = 200;

    private final OrderRepository orderRepository;
    private final BranchZoneRepository branchZoneRepository;

    @Transactional(readOnly = true)
    public List<OrderZoneMembershipDTO> memberships(String orderIds, Long branchId) {
        Long branch = LezSupport.requireBranch(branchId);
        List<Long> ids = parseOrderIds(orderIds);

        Map<Long, Order> ownOrders = new LinkedHashMap<>();
        for (Order order : orderRepository.findAllById(ids)) {
            if (order != null && order.getBranch() != null
                    && branch.equals(order.getBranch().getId())) {
                ownOrders.putIfAbsent(order.getId(), order);
            }
        }

        List<BranchZone> zones = branchZoneRepository.findByBranchIdOrderByIdAsc(branch).stream()
                .filter(zone -> Boolean.TRUE.equals(zone.getEnabled()))
                .toList();

        List<OrderZoneMembershipDTO> rows = new ArrayList<>();
        for (Long id : ids) {
            Order order = ownOrders.get(id);
            if (order == null) {
                continue;
            }
            rows.add(OrderZoneMembershipDTO.builder()
                    .orderId(order.getId())
                    .zones(zonesOf(order, zones))
                    .build());
        }
        return rows;
    }

    private static List<OrderZoneMembershipDTO.Zone> zonesOf(Order order, List<BranchZone> zones) {
        List<OrderZoneMembershipDTO.Zone> found = new ArrayList<>();
        Point location = order.getLocation();
        if (location == null) {
            return found;
        }
        for (BranchZone zone : zones) {
            MultiPolygon geometry = ZoneRuleLoader.effectiveGeometry(zone);
            if (GeoJsonGeometryParser.covers(geometry, location)) {
                found.add(OrderZoneMembershipDTO.Zone.builder()
                        .branchZoneId(zone.getId())
                        .name(nameOf(zone))
                        .build());
            }
        }
        return found;
    }

    /** Same naming rule as {@code BranchZoneDTO}: LINKED reads the template, COPIED its own name. */
    private static String nameOf(BranchZone zone) {
        return zone.getMode() == BranchZoneMode.LINKED
                ? zone.getSourceTemplate().getName()
                : zone.getName();
    }

    /**
     * Comma-separated ids; 1…200 entries, every entry a {@link Long}. Anything
     * else is a 400 through {@link LezSupport#invalid}, never a 500.
     */
    private static List<Long> parseOrderIds(String orderIds) {
        if (orderIds == null || orderIds.isBlank()) {
            throw LezSupport.invalid("orderIds is required");
        }
        String[] parts = orderIds.split(",", -1);
        if (parts.length > MAX_ORDERS) {
            throw LezSupport.invalid("orderIds must contain between 1 and " + MAX_ORDERS + " ids");
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                throw LezSupport.invalid("orderIds must be a comma-separated list of ids");
            }
            try {
                ids.add(Long.parseLong(trimmed));
            } catch (NumberFormatException e) {
                throw LezSupport.invalid("orderIds must be a comma-separated list of ids");
            }
        }
        if (ids.isEmpty()) {
            throw LezSupport.invalid("orderIds is required");
        }
        return new ArrayList<>(ids);
    }
}
