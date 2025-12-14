package org.truong.gvrp_entry_api.integration.external_api;

import org.truong.gvrp_entry_api.dto.request.EngineOptimizationRequest;

/**
 * Client interface for Optimization Engine
 * This allows us to have different implementations:
 * - MockOptimizationClient (for testing)
 * - RestOptimizationClient (for REST API)
 * - GrpcOptimizationClient (for gRPC)
 */
public interface EngineApiClient {

    void submitOptimizationAsync(Long jobId, EngineOptimizationRequest engineRequest);

}
