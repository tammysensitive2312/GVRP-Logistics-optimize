package org.truong.gvrp_entry_api.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderInputDTO {

    @NotBlank(message = "Order code is required")
    @Size(max = 50, message = "Order code must not exceed 50 characters")
    private String orderCode;

    @NotBlank(message = "Customer name is required")
    @Size(max = 100, message = "Customer name must not exceed 100 characters")
    private String customerName;

    @Pattern(regexp = "^[0-9+\\-\\s()]+$", message = "Invalid phone number format")
    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    private String customerPhone;

    @NotBlank(message = "Address is required")
    @Size(max = 255, message = "Address must not exceed 255 characters")
    private String address;

//    @NotNull(message = "Latitude is required")
    @Min(value = -90, message = "Latitude must be between -90 and 90")
    @Max(value = 90, message = "Latitude must be between -90 and 90")
    private Double latitude;

//    @NotNull(message = "Longitude is required")
    @Min(value = -180, message = "Longitude must be between -180 and 180")
    @Max(value = 180, message = "Longitude must be between -180 and 180")
    private Double longitude;

    @NotNull(message = "Demand is required")
    @DecimalMin(value = "0.01", message = "Demand must be greater than 0")
    private BigDecimal demand;

    @Min(value = 0, message = "Service time must be non-negative")
    private Integer serviceTime;

    private LocalTime timeWindowStart;

    private LocalTime timeWindowEnd;

    @Min(value = 1, message = "Priority must be at least 1")
    private Integer priority;

    @Size(max = 1000, message = "Delivery notes must not exceed 1000 characters")
    private String deliveryNotes;
}