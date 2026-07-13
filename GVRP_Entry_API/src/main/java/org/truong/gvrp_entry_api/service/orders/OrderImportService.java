package org.truong.gvrp_entry_api.service.orders;

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
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.service.integration.file.CsvFileParser;
import org.truong.gvrp_entry_api.service.integration.file.JsonFileParser;
import org.truong.gvrp_entry_api.service.integration.file.ParseResult;
import org.truong.gvrp_entry_api.service.integration.file.TextDataParser;
import org.truong.gvrp_entry_api.util.ErrorCode;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.mapper.OrderMapper;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.service.GeocodingService;
import org.truong.gvrp_entry_api.util.AppConstant;

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
    private final TextDataParser dataParser;

    private final Validator validator;

    @Transactional
    public ImportResultDTO createOrdersByImportRequest(OrderImportRequest request, Long branchId) {

        log.info("Importing orders for Branch ID: {}", branchId);
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new DataInvalidException(List.of(
                        ErrorDetail.builder()
                                .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                                .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                                .resource(AppConstant.BRANCH)
                                .build()
                )));

        List<OrderInputDTO> allRawOrders = new ArrayList<>();
        List<ImportError> allErrors = new ArrayList<>();
        Integer choice = request.getChoice();

        switch (choice) {
            case 0:
                throw new DataInvalidException(List.of(
                        ErrorDetail.builder()
                                .code(ErrorCode.UNSUPPORTED_VALUE.getCode())
                                .message("Request has both file and text input")
                                .field(AppConstant.FILE)
                                .build()
                ));
            case 1:
                throw new DataInvalidException(List.of(
                        ErrorDetail.builder()
                                .code(ErrorCode.UNSUPPORTED_VALUE.getCode())
                                .message("Request has no file and text input")
                                .field(AppConstant.FILE)
                                .build()
                ));
            case 2: {
                MultipartFile file = request.getFile();
                String filename = file != null ? file.getOriginalFilename() : null;

                ParseResult<OrderInputDTO> fileResult;
                try {
                    if (filename != null && filename.toLowerCase().endsWith(".csv")) {
                        fileResult = csvFileParser.parse(file);
                    } else if (filename != null && filename.toLowerCase().endsWith(".json")) {
                        fileResult = jsonFileParser.parse(file);
                    } else {
                        throw new DataInvalidException(List.of(
                                ErrorDetail.builder()
                                        .code(ErrorCode.UNSUPPORTED_VALUE.getCode())
                                        .message("Định dạng file không hỗ trợ (chỉ CSV/JSON).")
                                        .field(AppConstant.FILE)
                                        .build()
                        ));
                    }

                    if (fileResult != null) {
                        allRawOrders.addAll(fileResult.getValidItems());
                        allErrors.addAll(fileResult.getErrors());
                    }
                } catch (DataInvalidException e) {
                    throw e;
                } catch (Exception e) {
                    log.error("Lỗi hệ thống khi đọc file: {}", e.getMessage(), e);
                    throw new DataInvalidException(List.of(
                            ErrorDetail.builder()
                                    .code(ErrorCode.BACKEND_SERVER_ERROR.getCode())
                                    .message(ErrorCode.BACKEND_SERVER_ERROR.getMessage())
                                    .field(AppConstant.FILE)
                                    .build()
                    ));
                }
                break;
            }
            case 3: {
                log.info("Parsing text data input");
                ParseResult<OrderInputDTO> textResult = dataParser.parse(request.getTextData());
                allRawOrders.addAll(textResult.getValidItems());
                allErrors.addAll(textResult.getErrors());

                log.debug("Parsed {} orders from text data with {} errors",
                        textResult.getValidItems().size(), textResult.getErrors().size());
                break;
            }
            case 4:
                throw new RuntimeException("Unexpected backend server error simulation");
            default:
                throw new DataInvalidException(List.of(
                        ErrorDetail.builder()
                                .code(ErrorCode.BACKEND_SERVER_ERROR.getCode())
                                .code(ErrorCode.BACKEND_SERVER_ERROR.getCode())
                                .build()
                ));
        }

        return processParsedData(allRawOrders, allErrors, request, branch);
    }

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

        return saveOrdersToDatabase(validOrdersToSave, branch, request, validationErrors);
    }

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
                        skippedCount++;
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

        int totalProcessed = importedCount + skippedCount + dbErrors.size();

        return ImportResultDTO.builder()
                .success(true)
                .totalRecords(totalProcessed)
                .importedCount(importedCount)
                .skippedCount(skippedCount)
                .errors(dbErrors)
                .requiresConfirmation(false)
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