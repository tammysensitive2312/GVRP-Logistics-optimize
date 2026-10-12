package org.truong.gvrp_entry_api.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class RevisionDTO {
    private Long id;
    private LocalDateTime createdAt;
    private String type;
    private String status;
}
