package org.truong.gvrp_entry_api.service.integration.external_api;

import org.truong.gvrp_entry_api.dto.request.EngineOptimizationRequest;

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

}
