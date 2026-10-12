package org.truong.gvrp_entry_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.truong.gvrp_entry_api.entity.RouteSend;
import org.truong.gvrp_entry_api.entity.enums.SendStatus;

import java.util.List;

public interface RouteSendRepository extends JpaRepository<RouteSend, Long> {

    List<RouteSend> findByRouteIdOrderBySentAtDescIdDesc(Long routeId);

    List<RouteSend> findByDriverIdOrderBySentAtDescIdDesc(Long driverId);

    List<RouteSend> findByDriverIdAndStatusOrderBySentAtDescIdDesc(Long driverId, SendStatus status);

    List<RouteSend> findByRouteIdAndStatusOrderBySentAtDescIdDesc(Long routeId, SendStatus status);
}
