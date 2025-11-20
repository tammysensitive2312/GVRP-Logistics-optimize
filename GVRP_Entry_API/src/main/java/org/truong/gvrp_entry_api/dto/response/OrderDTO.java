package org.truong.gvrp_entry_api.dto.response;

import org.truong.gvrp_entry_api.entity.enums.OrderStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDTO {

    private Long id;
    private Long branchId;
    private String orderCode;
    private String customerName;
    private String customerPhone;
    private String address;
    private Double latitude;
    private Double longitude;
    private Double demand;
    private Integer serviceTime;
    private LocalTime timeWindowStart;
    private LocalTime timeWindowEnd;
    private LocalDate deliveryDate;
    private OrderStatus status;
    private Integer priority;
    private String deliveryNotes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
