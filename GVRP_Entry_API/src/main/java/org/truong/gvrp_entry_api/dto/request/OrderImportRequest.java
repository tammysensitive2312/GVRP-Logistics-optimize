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

        private boolean hasFile() {
            return file != null && !file.isEmpty();
        }

        private boolean hasText() {
            return textData != null && !textData.trim().isEmpty();
        }

        private boolean hasBothInputs() {
            return hasFile() && hasText();
        }

        private boolean hasNoInput() {
            return !hasFile() && !hasText();
        }

        public Integer getChoice() {
            if (hasBothInputs()) {
                return 0;
            } else if (hasNoInput()) {
                return 1;
            } else if (hasFile()) {
                return 2;
            } else if (hasText()) {
                return 3;
            } else {
                return 4;
            }
        }
    }
