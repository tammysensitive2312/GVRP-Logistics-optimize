package org.truong.gvrp_entry_api.service.integration.file;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.truong.gvrp_entry_api.dto.request.OrderInputDTO;
import org.truong.gvrp_entry_api.dto.response.ImportError;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.util.ErrorCode;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.util.AppConstant;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@Slf4j
public class JsonFileParser implements FileParser<OrderInputDTO> {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private final ObjectMapper objectMapper;

    public JsonFileParser() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public ParseResult<OrderInputDTO> parse(MultipartFile file) {
        validateFile(file);

        List<OrderInputDTO> validOrders = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream()) {
            JsonParser parser = objectMapper.getFactory().createParser(inputStream);

            if (parser.nextToken() != JsonToken.START_ARRAY) {
                ErrorDetail errorDetail = ErrorDetail.builder()
                        .code("0040001")
                        .message("JSON root must be an array [...]")
                        .resource("file")
                        .build();
                throw new DataInvalidException(List.of(errorDetail));
            }

            while (parser.nextToken() != JsonToken.END_ARRAY) {
                int currentLine = parser.getCurrentLocation().getLineNr();

                try {
                    OrderInputDTO order = objectMapper.readValue(parser, OrderInputDTO.class);
                    validOrders.add(order);

                } catch (JsonMappingException e) {
                    log.warn("JSON mapping error at line {}: {}", currentLine, e.getMessage());

                    String fieldName = e.getPath().stream()
                            .map(JsonMappingException.Reference::getFieldName)
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining("."));

                    errors.add(ImportError.builder()
                            .lineNumber(currentLine)
                            .field(fieldName.isEmpty() ? null : fieldName)
                            .errorMessage(simplifyErrorMessage(e))
                            .rawData("JSON Object at line " + currentLine)
                            .build());

                } catch (JsonParseException e) {
                    // Lỗi cú pháp JSON (vd: thiếu dấu phẩy, thiếu ngoặc kép)
                    log.warn("JSON parse error at line {}: {}", currentLine, e.getMessage());

                    errors.add(ImportError.builder()
                            .lineNumber(currentLine)
                            .errorMessage(simplifyErrorMessage(e))
                            .rawData("JSON Object at line " + currentLine)
                            .build());
                }
            }

            log.info("JSON parsing completed. Valid: {}, Errors: {}", validOrders.size(), errors.size());
            return new ParseResult<>(validOrders, errors);

        } catch (IOException e) {
            ErrorDetail errorDetail = ErrorDetail.builder()
                    .code(ErrorCode.INTERNAL_ERROR.getCode())
                    .message(ErrorCode.INTERNAL_ERROR.getMessage())
                    .resource(AppConstant.FILE)
                    .build();
            throw new DataInvalidException(List.of(errorDetail));

        } catch (Exception e) {
            if (e instanceof DataInvalidException) {
                throw (DataInvalidException) e;
            }
            throw new RuntimeException("Unexpected error during JSON parsing", e);
        }
    }

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
            if (filename == null || !filename.toLowerCase().endsWith(".json")) {
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

    private String simplifyErrorMessage(Exception e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("\n")) {
            return msg.substring(0, msg.indexOf("\n"));
        }
        return msg;
    }
}