package org.truong.gvrp_entry_api.service.integration.file;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.truong.gvrp_entry_api.dto.request.OrderInputDTO;
import org.truong.gvrp_entry_api.dto.response.ImportError;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.util.ErrorCode;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.util.AppConstant;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser cho CSV files để import orders.
 * Hỗ trợ auto-detect headers, case-insensitive columns, và xử lý lỗi chi tiết.
 */
@Component
@Slf4j
public class CsvFileParser implements FileParser<OrderInputDTO> {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Parse orders từ CSV file.
     *
     * @param file CSV file từ MultipartFile
     * @return ParseResult chứa danh sách orders hợp lệ và errors
     */
    public ParseResult<OrderInputDTO> parse(MultipartFile file) {
        validateFile(file);

        List<OrderInputDTO> validOrders = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreHeaderCase(true)
                    .setTrim(true)
                    .setIgnoreEmptyLines(true)
                    .setDelimiter(',')
                    .build();

            CSVParser csvParser = new CSVParser(reader, csvFormat);
            int lineNumber = 1;

            for (CSVRecord record : csvParser) {
                lineNumber++;

                try {
                    OrderInputDTO order = parseRecord(record, lineNumber);
                    validOrders.add(order);

                } catch (ParseException e) {
                    log.warn("Failed to parse line {}: {}", lineNumber, e.getMessage());
                    errors.add(ImportError.builder()
                            .lineNumber(lineNumber)
                            .orderCode(safeGet(record, "orderCode"))
                            .field(e.getField())
                            .errorMessage(e.getMessage())
                            .rawData(record.toString())
                            .build());
                }
            }

            log.info("CSV parsing completed. Valid: {}, Errors: {}", validOrders.size(), errors.size());
            return new ParseResult<>(validOrders, errors);

        } catch (IOException e) {
            ErrorDetail errorDetail = ErrorDetail.builder()
                    .code(ErrorCode.INTERNAL_ERROR.getCode())
                    .message(ErrorCode.INTERNAL_ERROR.getMessage())
                    .resource(AppConstant.FILE)
                    .build();
            throw new DataInvalidException(List.of(errorDetail));

        } catch (Exception e) {
            throw new RuntimeException("Unexpected error during CSV parsing", e);
        }
    }

    /**
     * Validate file trước khi parse, gom tất cả lỗi vào một List và ném DataInvalidException.
     */
    private void validateFile(MultipartFile file) {
        List<ErrorDetail> fileErrors = new ArrayList<>();

        if (file == null || file.isEmpty()) {
            fileErrors.add(ErrorDetail.builder()
                    .code(ErrorCode.EMPTY_FIELD_ERROR.getCode())
                    .message(ErrorCode.EMPTY_FIELD_ERROR.getMessage())
                    .resource(AppConstant.FILE)
                    .build());
        } else {
            if (file.getSize() > MAX_FILE_SIZE) {
                fileErrors.add(ErrorDetail.builder()
                        .code(ErrorCode.FILE_SIZE_EXCEEDED.getCode())
                        .message(ErrorCode.FILE_SIZE_EXCEEDED.getMessage())
                        .resource(AppConstant.FILE)
                        .build());
            }

            String filename = file.getOriginalFilename();
            if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
                fileErrors.add(ErrorDetail.builder()
                        .code(ErrorCode.VALIDATION_ERROR.getCode())
                        .message(ErrorCode.VALIDATION_ERROR.getMessage())
                        .resource(AppConstant.FILE)
                        .build());
            }
        }

        if (!fileErrors.isEmpty()) {
            throw new DataInvalidException(fileErrors);
        }
    }

    private OrderInputDTO parseRecord(CSVRecord record, int lineNumber) throws ParseException {
        try {
            return OrderInputDTO.builder()
                    .orderCode(parseRequiredString(record, "orderCode", lineNumber))
                    .customerName(parseRequiredString(record, "customerName", lineNumber))
                    .customerPhone(parseOptionalString(record, "customerPhone"))
                    .address(parseRequiredString(record, "address", lineNumber))
                    .demand(parseRequiredBigDecimal(record, "demand", lineNumber))
                    .serviceTime(parseOptionalInteger(record, "serviceTime"))
                    .timeWindowStart(parseOptionalTime(record, "timeWindowStart"))
                    .timeWindowEnd(parseOptionalTime(record, "timeWindowEnd"))
                    .priority(parseOptionalInteger(record, "priority"))
                    .deliveryNotes(parseOptionalString(record, "deliveryNotes"))
                    .build();

        } catch (ParseException e) {
            throw e;
        } catch (Exception e) {
            throw new ParseException("Unknown error", lineNumber, e.getMessage());
        }
    }

    private String parseRequiredString(CSVRecord record, String columnName, int lineNumber) throws ParseException {
        String value = safeGet(record, columnName);
        if (value == null || value.trim().isEmpty()) {
            throw new ParseException(columnName, lineNumber, String.format("Field '%s' is required", columnName));
        }
        return value.trim();
    }

    private BigDecimal parseRequiredBigDecimal(CSVRecord record, String columnName, int lineNumber) throws ParseException {
        String value = safeGet(record, columnName);
        if (value == null || value.trim().isEmpty()) {
            throw new ParseException(columnName, lineNumber, String.format("Field '%s' is required", columnName));
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new ParseException(columnName, lineNumber, String.format("Invalid decimal format for '%s': %s", columnName, value));
        }
    }

    private String parseOptionalString(CSVRecord record, String columnName) {
        String value = safeGet(record, columnName);
        return (value != null && !value.trim().isEmpty()) ? value.trim() : null;
    }

    private Integer parseOptionalInteger(CSVRecord record, String columnName) {
        String value = safeGet(record, columnName);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid integer for column '{}': {}. Setting to null.", columnName, value);
            return null;
        }
    }

    private LocalTime parseOptionalTime(CSVRecord record, String columnName) {
        String value = safeGet(record, columnName);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim(), TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            log.warn("Invalid time format for column '{}': {}. Expected HH:mm. Setting to null.", columnName, value);
            return null;
        }
    }

    private String safeGet(CSVRecord record, String columnName) {
        try {
            if (record.isMapped(columnName)) {
                return record.get(columnName);
            }
            String[] variations = {
                    columnName.toLowerCase(),
                    columnName.toUpperCase(),
                    camelToSnake(columnName),
                    snakeToCamel(columnName)
            };
            for (String variation : variations) {
                if (record.isMapped(variation)) {
                    return record.get(variation);
                }
            }
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String camelToSnake(String str) {
        return str.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    private String snakeToCamel(String str) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;
        for (char c : str.toCharArray()) {
            if (c == '_') {
                capitalizeNext = true;
            } else {
                result.append(capitalizeNext ? Character.toUpperCase(c) : c);
                capitalizeNext = false;
            }
        }
        return result.toString();
    }

    @Getter
    public static class ParseException extends Exception {
        private final String field;
        private final int lineNumber;

        public ParseException(String field, int lineNumber, String message) {
            super(message);
            this.field = field;
            this.lineNumber = lineNumber;
        }
    }
}