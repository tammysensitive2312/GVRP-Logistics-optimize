package org.truong.gvrp_entry_api.integration.file;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.truong.gvrp_entry_api.dto.request.OrderInputDTO;
import org.truong.gvrp_entry_api.dto.response.ImportError;


import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class TextDataParser { // If there is a DataParser interface, implement it here

    private static final String SEPARATOR = "\\|"; // Split by character '|'

    public ParseResult<OrderInputDTO> parse(String data) {
        List<OrderInputDTO> validOrders = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();

        if (data == null || data.trim().isEmpty()) {
            return new ParseResult<>(validOrders, errors);
        }

        String[] lines = data.split("\\r?\\n"); // Split by line

        for (int i = 0; i < lines.length; i++) {
            int lineNumber = i + 1;
            String line = lines[i].trim();

            // Skip empty lines
            if (line.isEmpty()) continue;

            // split(regex, -1) to keep empty strings if data is missing at the end
            String[] fields = line.split(SEPARATOR, -1);

            // Check minimum number of fields
            if (fields.length < 10) {
                log.warn("Line {}: Missing fields. Found {}, required 12.", lineNumber, fields.length);
                errors.add(ImportError.builder()
                        .lineNumber(lineNumber)
                        .errorMessage("Line missing data. Required 12 fields, found " + fields.length)
                        .rawData(line)
                        .build());
                continue;
            }

            // Get orderCode first to use in error reporting (if any)
            String orderCode = fields[0].trim();

            try {
                // Mapping order:
                // 0:Code | 1:Name | 2:Phone | 3:Address |
                // 4:Demand | 5:TWS | 6:TWE | 7:Prio | 8:Notes | 9:STime

                OrderInputDTO dto = OrderInputDTO.builder()
                        .orderCode(parseRequiredString(fields[0], "Order Code"))
                        .customerName(parseRequiredString(fields[1], "Customer Name"))
                        .customerPhone(parseOptionalString(fields[2]))
                        .address(parseRequiredString(fields[3], "Address"))

                        .demand(parseBigDecimal(fields[4], "Demand"))

                        .timeWindowStart(parseTime(fields[5], "Time Window Start"))
                        .timeWindowEnd(parseTime(fields[6], "Time Window End"))

                        .priority(parseInteger(fields[7], "Priority"))
                        .deliveryNotes(parseOptionalString(fields[8]))
                        .serviceTime(parseInteger(fields[9], "Service Time"))
                        .build();

                validOrders.add(dto);

            } catch (IllegalArgumentException e) {
                // Catch format errors thrown by helper methods
                errors.add(ImportError.builder()
                        .lineNumber(lineNumber)
                        .orderCode(orderCode.isEmpty() ? "UNKNOWN" : orderCode)
                        .errorMessage(e.getMessage()) // Detailed message from helper (e.g., "Invalid number format in Latitude field")
                        .rawData(line)
                        .build());

            } catch (Exception e) {
                // Catch unexpected errors
                log.error("Unexpected error at line {}: {}", lineNumber, e.getMessage());
                errors.add(ImportError.builder()
                        .lineNumber(lineNumber)
                        .orderCode(orderCode)
                        .errorMessage("Error processing line: " + e.getMessage())
                        .rawData(line)
                        .build());
            }
        }

        log.info("Text parsing completed. Valid: {}, Errors: {}", validOrders.size(), errors.size());
        return new ParseResult<>(validOrders, errors);
    }

    // =========================================================================
    // HELPER METHODS (Make code cleaner and report exactly which field is wrong)
    // =========================================================================

    private String parseRequiredString(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Required field '" + fieldName + "' is empty.");
        }
        return value.trim();
    }

    private String parseOptionalString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private BigDecimal parseBigDecimal(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Field '" + fieldName + "' is required.");
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid BigDecimal format in field '" + fieldName + "': " + value);
        }
    }

    private Integer parseInteger(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return null; // Priority/ServiceTime can be null depending on logic, assume nullable here
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer format in field '" + fieldName + "': " + value);
        }
    }

    private LocalTime parseTime(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim()); // Default ISO format (HH:mm or HH:mm:ss)
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid time format in field '" + fieldName + "'. Required HH:mm: " + value);
        }
    }
}
