package org.truong.gvrp_entry_api.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DriverDTO {
    private Long id;
    private String username;
    private String fullName;
    private String phone;
}
