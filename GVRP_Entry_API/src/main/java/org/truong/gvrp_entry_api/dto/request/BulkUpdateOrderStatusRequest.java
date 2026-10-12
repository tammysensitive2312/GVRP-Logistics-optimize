package org.truong.gvrp_entry_api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class BulkUpdateOrderStatusRequest {
    @NotNull
    @NotEmpty
    private List<@NotNull Long> ids;

    @NotNull
    private String status;
}
