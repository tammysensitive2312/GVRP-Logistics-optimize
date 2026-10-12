package org.truong.gvrp_entry_api.entity;

import jakarta.persistence.*;
import lombok.*;
import org.truong.gvrp_entry_api.entity.enums.SendStatus;

import java.time.LocalDateTime;

/**
 * Immutable record of a route plan sent to a driver.
 *
 * <p>A newer send for the same route flips siblings to SUPERSEDED instead of
 * deleting them, keeping a full dispatch history.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "route_sends", indexes = {
        @Index(name = "idx_send_route", columnList = "route_id"),
        @Index(name = "idx_send_driver", columnList = "driver_id"),
        @Index(name = "idx_send_solution", columnList = "solution_id")
})
public class RouteSend extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solution_id", nullable = false)
    private Solution solution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SendStatus status = SendStatus.CURRENT;
}
