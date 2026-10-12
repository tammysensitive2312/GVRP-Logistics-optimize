package org.truong.gvrp_entry_api.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class BulkUpdateOrderStatusResponse {
    private List<UpdatedRow> updated;
    private List<FailedRow> failed;

    @Getter
    @Setter
    @Builder
    public static class UpdatedRow {
        private Long id;
        private String status;
    }

    @Getter
    @Setter
    @Builder
    public static class FailedRow {
        private Long id;
        private String orderCode;
        private String reason;
    }
}
