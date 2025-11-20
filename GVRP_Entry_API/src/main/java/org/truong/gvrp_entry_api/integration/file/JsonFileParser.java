package org.truong.gvrp_entry_api.integration.file;

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
import org.truong.gvrp_entry_api.exception.InvalidFileFormatException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class JsonFileParser implements FileParser<OrderInputDTO> {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private final ObjectMapper objectMapper;

    public JsonFileParser() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        // Cho phép import linh hoạt hơn, bỏ qua các trường thừa trong JSON nếu DTO không có
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public ParseResult<OrderInputDTO> parse(MultipartFile file) throws InvalidFileFormatException {
        validateFile(file);

        List<OrderInputDTO> validOrders = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream()) {
            // Tạo parser để đọc luồng (streaming) thay vì đọc toàn bộ vào memory
            JsonParser parser = objectMapper.getFactory().createParser(inputStream);

            // Kiểm tra xem root có phải là Array không (JSON import phải là mảng [])
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new InvalidFileFormatException("JSON root must be an array [...]");
            }

            // Lặp qua từng phần tử trong mảng
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                // Lưu lại vị trí dòng hiện tại để báo lỗi nếu cần
                int currentLine = parser.getCurrentLocation().getLineNr();

                try {
                    // Đọc từng object đơn lẻ
                    OrderInputDTO order = objectMapper.readValue(parser, OrderInputDTO.class);
                    validOrders.add(order);

                } catch (JsonParseException | JsonMappingException e) {
                    // Nếu object này lỗi, ghi lại và tiếp tục loop
                    log.warn("JSON parse error at line {}: {}", currentLine, e.getMessage());

                    errors.add(ImportError.builder()
                            .lineNumber(currentLine)
                            .errorMessage(simplifyErrorMessage(e))
                            .rawData("JSON Object at line " + currentLine) // JSON raw khó lấy chuẩn khi stream, ta chỉ định vị trí
                            .build());
                }
            }

            log.info("JSON parsing completed. Valid: {}, Errors: {}", validOrders.size(), errors.size());
            return new ParseResult<>(validOrders, errors);

        } catch (IOException e) {
            throw new InvalidFileFormatException("Error reading JSON file: " + e.getMessage());
        }
    }

    private void validateFile(MultipartFile file) throws InvalidFileFormatException {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileFormatException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidFileFormatException("File size exceeds limit");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".json")) {
            throw new InvalidFileFormatException("Only JSON files are accepted");
        }
    }

    // Rút gọn thông báo lỗi của Jackson cho user dễ đọc hơn
    private String simplifyErrorMessage(Exception e) {
        String msg = e.getMessage();
        if (msg.contains("\n")) {
            return msg.substring(0, msg.indexOf("\n")); // Lấy dòng đầu tiên
        }
        return msg;
    }
}
