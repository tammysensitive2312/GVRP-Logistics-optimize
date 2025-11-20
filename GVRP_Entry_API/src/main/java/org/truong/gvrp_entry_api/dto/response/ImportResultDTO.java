package org.truong.gvrp_entry_api.dto.response;

import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportResultDTO {

    private Boolean success;
    private Integer importedCount;
    private Integer skippedCount;
    private Integer totalRecords;
    private Boolean requiresConfirmation;

    @Builder.Default
    private List<ImportError> errors = new ArrayList<>();

    // Helper methods
    public boolean isPartialSuccess() {
        return success && !errors.isEmpty();
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public void addError(ImportError error) {
        this.errors.add(error);
    }
}
