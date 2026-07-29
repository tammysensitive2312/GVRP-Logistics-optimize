package org.truong.gvrp_entry_api.exception;

/**
 * Callback từ engine tới sớm hơn khả năng tiếp nhận của Entry — job vẫn còn
 * {@code PENDING}.
 *
 * <p><b>Bối cảnh race condition.</b> {@code OptimizationJobService.submitJob} tạo job
 * ở trạng thái {@code PENDING} rồi mới gọi engine trong {@code afterCommit}. Phía
 * {@code EngineApiClientImpl.submitOptimizationAsync} lại POST trước, cập nhật
 * {@code PROCESSING} sau khi engine trả 202. Với job nhỏ, engine có thể giải xong và
 * bắn callback về trước khi Entry kịp ghi {@code PROCESSING}.
 *
 * <p><b>Vì sao phải ném thay vì bỏ qua.</b> "Chưa sẵn sàng" khác hẳn "bỏ qua vĩnh
 * viễn". Nếu nuốt và trả 200, engine coi là đã giao thành công, chuyển payload sang
 * {@code sent/} và không bao giờ gửi lại — kết quả nằm trên đĩa engine nhưng vĩnh
 * viễn không vào DB, trong khi log báo thành công. Ném ra để thành 503, engine phân
 * loại là lỗi tạm thời, giữ payload trong {@code pending/} và retry sau ~60s, lúc đó
 * job đã kịp sang {@code PROCESSING}.
 *
 * <p>Được xử lý riêng trong {@code GlobalExceptionHandler} để trả 503 kèm log gọn,
 * thay vì rơi vào handler chung và bị ghi thành "Unexpected system error" với nguyên
 * stack trace cho một tình huống hoàn toàn lành tính.
 */
public class JobNotReadyException extends RuntimeException {

    public JobNotReadyException(String message) {
        super(message);
    }
}
