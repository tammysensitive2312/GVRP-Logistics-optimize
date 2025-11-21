package org.truong.gvrp_entry_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.truong.gvrp_entry_api.dto.request.OrderImportRequest;
import org.truong.gvrp_entry_api.dto.request.OrderInputDTO;
import org.truong.gvrp_entry_api.dto.response.ImportError;
import org.truong.gvrp_entry_api.dto.response.ImportResultDTO;
import org.truong.gvrp_entry_api.dto.response.OrderDTO;
import org.truong.gvrp_entry_api.dto.response.PageResponse;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderImportService {

    private final ObjectMapper objectMapper;

    private final BranchRepository branchRepository;
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    private final CsvFileParser csvFileParser;
    private final JsonFileParser jsonFileParser;
    private final TextDataParser textDataParser;

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

        // 2. Xử lý File (CSV/JSON)
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
                // Bắt lỗi đọc file nghiêm trọng (VD: file hỏng hoàn toàn)
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

            if (dto.getServiceTime()== null) {
                dto.setServiceTime(request.getServiceTime());
            }

            // Business Validation (Check logic, tọa độ, demand...)
            String errorMsg = validate(dto);
            if (errorMsg != null) {
                validationErrors.add(buildError(
                        i + 1, // Line number giả định (tương đối)
                        dto.getOrderCode(),
                        "BusinessRule",
                        errorMsg,
                        "N/A"
                ));
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

                        orderMapper.updateEntityFromDTO(dto, existingOrder, request.getDeliveryDate());
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

    public String validate(OrderInputDTO dto) {
        // 1. Validate Coordinates
        if (dto.getLatitude() == null || dto.getLongitude() == null) {
            return "Tọa độ (Latitude/Longitude) là bắt buộc.";
        }
        if (dto.getLatitude() < -90 || dto.getLatitude() > 90 || dto.getLongitude() < -180 || dto.getLongitude() > 180) {
            return "Tọa độ không hợp lệ (Lat: -90~90, Lng: -180~180)";
        }

        // 2. Validate Demand
        if (dto.getDemand() == null || dto.getDemand().compareTo(BigDecimal.ZERO) <= 0) {
            return "Demand phải là số dương (> 0).";
        }

        // 3. Validate Time Window
        if (dto.getTimeWindowStart() != null && dto.getTimeWindowEnd() != null) {
            if (dto.getTimeWindowStart().isAfter(dto.getTimeWindowEnd())) {
                return "Thời gian bắt đầu Time Window không được sau thời gian kết thúc.";
            }
        } else if (dto.getTimeWindowStart() != null || dto.getTimeWindowEnd() != null) {
            return "Time Window phải có đủ thời gian bắt đầu và kết thúc hoặc không có cả hai.";
        }

        // 4. Validate Service Time (đã được enrich default nếu null)
        if (dto.getServiceTime() == null || dto.getServiceTime() < 0) {
            return "Service time không hợp lệ (phải >= 0).";
        }

        return null; // Valid
    }

    public PageResponse<OrderDTO> getAllOrdersPaginated(Long branchId, int pageNo, int pageSize) {
        // 1. Tạo đối tượng Pageable (có thể thêm Sort nếu muốn, ví dụ sort theo ID giảm dần)
        Pageable pageable = PageRequest.of(pageNo, pageSize, Sort.by("id").descending());

        // 2. Gọi Repository
        Page<Order> orderPage = orderRepository.findByBranchIdOrderByCreatedAtDesc(branchId, pageable);

        // 3. Map Entity sang DTO
        // Lưu ý: orderPage.getContent() trả về List<Order>
        List<OrderDTO> content = orderMapper.toDTOList(orderPage.getContent());

        // 4. Build PageResponse
        return PageResponse.<OrderDTO>builder()
                .content(content)
                .pageNo(orderPage.getNumber())
                .pageSize(orderPage.getSize())
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .last(orderPage.isLast())
                .build();
    }
}