package org.truong.gvrp_entry_api.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportError {

    private Integer lineNumber;
    private String orderCode;
    private String field;
    private String errorMessage;
    private String rawData;
}
