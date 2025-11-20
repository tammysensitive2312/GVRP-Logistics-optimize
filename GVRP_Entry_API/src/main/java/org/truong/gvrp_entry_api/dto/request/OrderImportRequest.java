package org.truong.gvrp_entry_api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderImportRequest {

    private MultipartFile file;
    private String textData;

    @NotNull(message = "Delivery date is required")
    private LocalDate deliveryDate;

    @Min(value = 0, message = "Service time must be non-negative")
    private Integer serviceTime;

    private Boolean skipValidationErrors;

    private Boolean overwriteExisting;

    // Helper methods
    public boolean hasFile() {
        return file != null && !file.isEmpty();
    }

    public boolean hasText() {
        return textData != null && !textData.trim().isEmpty();
    }

    public boolean hasBothInputs() {
        return hasFile() && hasText();
    }

    public boolean hasNoInput() {
        return !hasFile() && !hasText();
    }
}
