package org.truong.gvrp_engine_api.job;

/**
 * Ném ra khi một optimization job bị yêu cầu hủy.
 * <p>
 * Dùng chung cho cả hai giai đoạn:
 * - Trong lúc dựng ma trận (DistanceMatrixService kiểm cờ cancel giữa các hàng).
 * - Trong lúc solve (OptimizationService kiểm cờ sau khi solver dừng "êm" qua
 *   PrematureAlgorithmTermination).
 * <p>
 * OptimizationService.optimizeAsync bắt exception này để set trạng thái CANCELLED
 * và gửi cancelled callback (thay vì completion/failure).
 */
public class JobCancelledException extends RuntimeException {
    public JobCancelledException(String message) {
        super(message);
    }
}
