# Context — SEC-ORDERS-001

Inspected 2026-11-04 by Claude Code (`c0cdfd5`).

- `GVRP_Entry_API/.../service/orders/OrderService.java#bulkUpdateStatus(branchId, ids, newStatus)`: `orderRepository.findAllById(ids)` (unscoped), then for each id: missing or `order.getBranch().getId() != branchId` → `FailedRow.id(id).orderCode(order != null ? order.getOrderCode() : null).reason("Order not found in this branch")`. Own-branch orders go to `transitionService.bulkTransitionLenient`.
- Controller: `OrderController` `PATCH /api/v1/orders/status`, branch from `CurrentUserUtil.getCurrentBranchId()`.
- Tests: `service/orders/OrderServiceTest.java` stubs `orderRepository.findAllById(List.of(11L, 12L, 13L, 14L))` with a foreign order 13 (branch 2) and asserts failed ids `{12, 13, 14}` and the reason of 12 — it does not assert the foreign code. `controller/OrderBulkControllerTest.java` mocks the service. Keep the `findAllById` call so these stubs stay valid.
- Lessons: L-2026-001, L-2026-003, L-2026-007.
