package org.truong.gvrp_entry_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.truong.gvrp_entry_api.dto.request.OrderImportRequest;
import org.truong.gvrp_entry_api.dto.request.OrderInputDTO;
import org.truong.gvrp_entry_api.dto.response.ImportError;
import org.truong.gvrp_entry_api.dto.response.ImportResultDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.exception.InvalidFileFormatException;
import org.truong.gvrp_entry_api.exception.ResourceNotFoundException;
import org.truong.gvrp_entry_api.integration.file.CsvFileParser;
import org.truong.gvrp_entry_api.integration.file.JsonFileParser;
import org.truong.gvrp_entry_api.integration.file.ParseResult;
import org.truong.gvrp_entry_api.integration.file.TextDataParser;
import org.truong.gvrp_entry_api.mapper.OrderMapper;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.OrderRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderImportService {

    private final ObjectMapper objectMapper;
    private final GeocodingService geocodingService;

    private final BranchRepository branchRepository;
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    private final CsvFileParser csvFileParser;
    private final JsonFileParser jsonFileParser;
    private final TextDataParser textDataParser;

    private final Validator validator;

    /**
     * Entry point cho việc import.
     * Orchestrates: Parsing -> Validation -> Confirmation -> Saving
     */
    @Transactional
    public ImportResultDTO createOrdersByImportRequest(OrderImportRequest request, Long branchId) {

        log.info("Importing orders for Branch ID: {}", branchId);
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found", "Branch"));

        List<OrderInputDTO> allRawOrders = new ArrayList<>();
        List<ImportError> allErrors = new ArrayList<>();

        if (request.hasFile()) {
            MultipartFile file = request.getFile();
            String filename = file.getOriginalFilename();

            ParseResult<OrderInputDTO> fileResult = null;
            try {
                if (filename != null && filename.toLowerCase().endsWith(".csv")) {
                    fileResult = csvFileParser.parse(file);
                } else if (filename != null && filename.toLowerCase().endsWith(".json")) {
                    fileResult = jsonFileParser.parse(file);
                } else {
                    throw new InvalidFileFormatException("Định dạng file không hỗ trợ (chỉ CSV/JSON).");
                }

                if (fileResult != null) {
                    allRawOrders.addAll(fileResult.getValidItems());
                    allErrors.addAll(fileResult.getErrors());
                }
            } catch (Exception e) {
                log.debug("Lỗi đọc file: {}", e.getMessage());
                throw new InvalidFileFormatException("Lỗi đọc file: " + e.getMessage());
            }
        }

        // 3. Xử lý Text Data
        log.info("request has text data: {}", request.hasText());
        if (request.hasText()) {
            log.info("Parsing text data input");
            ParseResult<OrderInputDTO> textResult = textDataParser.parse(request.getTextData());
            allRawOrders.addAll(textResult.getValidItems());
            allErrors.addAll(textResult.getErrors());

            log.debug("Parsed {} orders from text data with {} errors",
                    textResult.getValidItems().size(), textResult.getErrors().size());
        }

        // 4. Chuyển tiếp đến logic xử lý dữ liệu đã parse
        return processParsedData(allRawOrders, allErrors, request, branch);
    }

    /**
     * Logic trung tâm: Validate Business Rules & Quyết định lưu
     */
    private ImportResultDTO processParsedData(List<OrderInputDTO> rawOrders,
                                              List<ImportError> parseErrors,
                                              OrderImportRequest request,
                                              Branch branch) {

        try {
            String rawOrdersJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rawOrders);
            String parseErrorsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parseErrors);
            String requestJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);

            log.debug("Raw Orders ({} items):\n{}", rawOrders.size(), rawOrdersJson);
            log.debug("Parse Errors ({} items):\n{}", parseErrors.size(), parseErrorsJson);
            log.debug("Order Import Request:\n{}", requestJson);
            log.debug("--------------------------------------");

        } catch (Exception e) {
            log.error("Lỗi khi serialize đối tượng để debug: {}", e.getMessage());
        }

        List<OrderInputDTO> validOrdersToSave = new ArrayList<>();
        List<ImportError> validationErrors = new ArrayList<>(parseErrors);

        // Bước 4.1: Enrich & Business Validation
        for (int i = 0; i < rawOrders.size(); i++) {
            OrderInputDTO dto = rawOrders.get(i);

            if (dto.getServiceTime() == null) {
                dto.setServiceTime(request.getServiceTime());
            }

            if ((dto.getLatitude() == null || dto.getLongitude() == null)
                    && dto.getAddress() != null && !dto.getAddress().trim().isEmpty()) {

                GeocodingService.GeocodeResult result = geocodingService.geocode(dto.getAddress());

                if (result != null) {
                    dto.setLatitude(result.lat);
                    dto.setLongitude(result.lng);
                } else {
                    validationErrors.add(buildError(
                            i + 1,
                            dto.getOrderCode(),
                            "address",
                            "The coordinates cannot be obtained from the address: " + dto.getAddress(),
                            dto.getAddress()
                    ));
                    // Không bỏ qua dòng này, nhưng đánh dấu lỗi → user sẽ thấy
                }
            }

            Set<ConstraintViolation<OrderInputDTO>> violations = validator.validate(dto, OrderInputDTO.OnCreate.class);

            if (!violations.isEmpty()) {
                String formatErrorMsg = violations.iterator().next().getMessage();
                validationErrors.add(buildError(i + 1, dto.getOrderCode(), "Format/Constraint", formatErrorMsg, "N/A"));
                continue;
            }

            String businessErrorMsg = validateBusinessLogic(dto);
            if (businessErrorMsg != null) {
                validationErrors.add(buildError(i + 1, dto.getOrderCode(), "BusinessRule", businessErrorMsg, "N/A"));
            } else {
                validOrdersToSave.add(dto);
            }
        }

        // Bước 4.2: Decision Gate (Cổng quyết định)
        // Nếu có lỗi VÀ user chưa xác nhận bỏ qua lỗi (skipValidationErrors = false)
        if (!validationErrors.isEmpty() && !Boolean.TRUE.equals(request.getSkipValidationErrors())) {
            return ImportResultDTO.builder()
                    .success(false)
                    .totalRecords(rawOrders.size() + parseErrors.size())
                    .importedCount(0)
                    .skippedCount(0)
                    .errors(validationErrors)
                    .requiresConfirmation(false)
                    .build();
        }

        // Bước 4.3: Lưu dữ liệu (Persistence)
        // Đến đây nghĩa là: Hoặc không có lỗi, Hoặc User đã đồng ý skip lỗi
        return saveOrdersToDatabase(validOrdersToSave, branch, request, validationErrors);
    }

    /**
     * Lưu danh sách đơn hàng hợp lệ vào Database
     */
    private ImportResultDTO saveOrdersToDatabase(List<OrderInputDTO> validOrders,
                                                 Branch branch,
                                                 OrderImportRequest request,
                                                 List<ImportError> existingErrors) {

        int importedCount = 0;
        int skippedCount = 0;
        List<ImportError> dbErrors = new ArrayList<>(existingErrors);

        for (OrderInputDTO dto : validOrders) {
            try {

                boolean exists = orderRepository.existsByBranchIdAndOrderCode(branch.getId(), dto.getOrderCode());

                if (exists) {
                    if (Boolean.TRUE.equals(request.getOverwriteExisting())) {

                        Order existingOrder = orderRepository.findByBranchIdAndOrderCode(branch.getId(), dto.getOrderCode())
                                .orElseThrow();

                        orderMapper.updateEntityFromDTO(dto, existingOrder);
                        log.info("entity service time value {}", existingOrder.getServiceTime());
                        orderRepository.save(existingOrder);
                        importedCount++;
                    } else {
                        // SKIP (Duplicate & Overwrite=false)
                        skippedCount++;
                        // Có thể thêm vào warning log hoặc list error nhẹ nếu muốn user biết
                    }
                } else {
                    Order newOrder = orderMapper.toEntity(dto, branch, request.getDeliveryDate());
                    orderRepository.save(newOrder);
                    importedCount++;
                }

            } catch (Exception e) {
                log.error("Failed to save order {}: {}", dto.getOrderCode(), e.getMessage());
                dbErrors.add(buildError(null, dto.getOrderCode(), "Database", "Lỗi lưu DB: " + e.getMessage(), null));
            }
        }

        // Tính tổng số bản ghi đã xử lý (bao gồm cả lỗi parse ban đầu)
        int totalProcessed = importedCount + skippedCount + dbErrors.size();

        return ImportResultDTO.builder()
                .success(true) // Transaction hoàn tất (dù có thể skip một số dòng lỗi)
                .totalRecords(totalProcessed)
                .importedCount(importedCount)
                .skippedCount(skippedCount)
                .errors(dbErrors)
                .requiresConfirmation(false) // Đã xong, không cần confirm nữa
                .build();
    }


    private ImportError buildError(Integer lineNumber, String orderCode, String field, String message, String rawData) {
        return ImportError.builder()
                .lineNumber(lineNumber)
                .orderCode(orderCode)
                .field(field)
                .errorMessage(message)
                .rawData(rawData)
                .build();
    }

    public String validateBusinessLogic(OrderInputDTO dto) {

        boolean hasCoordinates = dto.getLatitude() != null && dto.getLongitude() != null;
        boolean hasAddress = dto.getAddress() != null && !dto.getAddress().trim().isEmpty();

        if (!hasCoordinates && !hasAddress) {
            return "You must provide coordinates (latitude/longitude) OR a detailed address.";
        }

        if (dto.getTimeWindowStart() != null && dto.getTimeWindowEnd() != null) {
            if (dto.getTimeWindowStart().isAfter(dto.getTimeWindowEnd())) {
                return "Time Window start time cannot be after the end time.";
            }
        } else if (dto.getTimeWindowStart() != null || dto.getTimeWindowEnd() != null) {
            return "Time Window must have both start and end times, or neither.";
        }

        return null;
    }
}