package org.truong.gvrp_entry_api.service.integration.file;

import lombok.Getter;
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
public class TextDataParser implements DataParser {

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

            if (line.isEmpty()) continue;
            String[] fields = line.split(SEPARATOR, -1);

            if (fields.length < 10) {
                log.warn("Line {}: Missing fields. Found {}, required at least 10.", lineNumber, fields.length);
                errors.add(ImportError.builder()
                        .lineNumber(lineNumber)
                        .errorMessage("Line missing data. Required at least 10 fields, found " + fields.length)
                        .rawData(line)
                        .build());
                continue;
            }

            String orderCode = fields[0].trim();

            try {
                // Mapping order:
                // 0:Code | 1:Name | 2:Phone | 3:Address |
                // 4:Demand | 5:TWS | 6:TWE | 7:Prio | 8:Notes | 9:STime

                OrderInputDTO dto = OrderInputDTO.builder()
                        .orderCode(parseRequiredString(fields[0], "orderCode"))
                        .customerName(parseRequiredString(fields[1], "customerName"))
                        .customerPhone(parseOptionalString(fields[2]))
                        .address(parseRequiredString(fields[3], "address"))

                        .demand(parseBigDecimal(fields[4], "demand"))

                        .timeWindowStart(parseTime(fields[5], "timeWindowStart"))
                        .timeWindowEnd(parseTime(fields[6], "timeWindowEnd"))

                        .priority(parseInteger(fields[7], "priority"))
                        .deliveryNotes(parseOptionalString(fields[8]))
                        .serviceTime(parseInteger(fields[9], "serviceTime"))
                        .build();

                validOrders.add(dto);

            } catch (ParseException e) {
                errors.add(ImportError.builder()
                        .lineNumber(lineNumber)
                        .orderCode(orderCode.isEmpty() ? "UNKNOWN" : orderCode)
                        .field(e.getField())
                        .errorMessage(e.getMessage())
                        .rawData(line)
                        .build());

            } catch (Exception e) {
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

    private String parseRequiredString(String value, String fieldName) throws ParseException {
        if (value == null || value.trim().isEmpty()) {
            throw new ParseException(fieldName, "Required field '" + fieldName + "' is empty.");
        }
        return value.trim();
    }

    private String parseOptionalString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private BigDecimal parseBigDecimal(String value, String fieldName) throws ParseException {
        if (value == null || value.trim().isEmpty()) {
            throw new ParseException(fieldName, "Field '" + fieldName + "' is required.");
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new ParseException(fieldName, "Invalid BigDecimal format in field '" + fieldName + "': " + value);
        }
    }

    private Integer parseInteger(String value, String fieldName) throws ParseException {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ParseException(fieldName, "Invalid integer format in field '" + fieldName + "': " + value);
        }
    }

    private LocalTime parseTime(String value, String fieldName) throws ParseException {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new ParseException(fieldName, "Invalid time format in field '" + fieldName + "'. Required HH:mm: " + value);
        }
    }

    @Getter
    public static class ParseException extends Exception {
        private final String field;

        public ParseException(String field, String message) {
            super(message);
            this.field = field;
        }
    }
}