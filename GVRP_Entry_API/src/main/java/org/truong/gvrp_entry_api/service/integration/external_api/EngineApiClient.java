package org.truong.gvrp_entry_api.service.integration.external_api;

import org.truong.gvrp_entry_api.dto.request.EngineEvaluateRequestDTO;
import org.truong.gvrp_entry_api.dto.request.EngineLegsRequestDTO;
import org.truong.gvrp_entry_api.dto.request.EngineOptimizationRequest;
import org.truong.gvrp_entry_api.dto.response.EngineEvaluateResponseDTO;
import org.truong.gvrp_entry_api.dto.response.EngineLegsResponseDTO;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.EngineUnavailableException;

import java.util.List;
import java.util.Map;

/**
 * Client interface for Optimization Engine
 * This allows us to have different implementations:
 * - MockOptimizationClient (for testing)
 * - RestOptimizationClient (for REST API)
 * - GrpcOptimizationClient (for gRPC)
 */
public interface EngineApiClient {

    void submitOptimizationAsync(Long jobId, EngineOptimizationRequest engineRequest);

    /**
     * Lấy tiến độ real-time của job từ engine (proxy cho poll).
     * @return map tiến độ, hoặc null nếu engine không có job (chưa chạy / đã evict).
     */
    Map<String, Object> getProgress(Long jobId);

    /**
     * Gửi yêu cầu hủy job xuống engine (best-effort, không ném lỗi nếu engine không tới được).
     */
    void requestCancel(Long jobId);

    /**
     * Đánh giá đồng bộ một tập trình tự tuyến ứng viên (reorder / move-between-routes).
     *
     * @return kết quả evaluate của engine (feasible + violations + metrics tính lại)
     * @throws EngineUnavailableException khi engine không tới được hoặc lỗi 5xx
     * @throws DataInvalidException khi engine từ chối payload (400/413)
     */
    EngineEvaluateResponseDTO evaluateRoutes(EngineEvaluateRequestDTO request);

    /**
     * Asks the engine for the geometry and turn instructions of consecutive legs
     * (LEZ-001). One engine call routes every leg of one route.
     *
     * @param points ordered points of the route; at least two
     * @param locale instruction language tag, e.g. {@code vi}; null for the engine default
     * @return one leg per consecutive point pair; a leg may carry an error code
     * @throws EngineUnavailableException khi engine không tới được hoặc lỗi 5xx
     * @throws DataInvalidException khi engine từ chối payload (400)
     */
    EngineLegsResponseDTO legs(List<EngineLegsRequestDTO.Point> points, String locale);

}
