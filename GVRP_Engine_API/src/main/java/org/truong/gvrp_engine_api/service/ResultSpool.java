package org.truong.gvrp_engine_api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Outbox trên filesystem cho kết quả tối ưu hóa.
 *
 * <p>Engine không có DB, nên {@code OptimizationResult} chỉ sống trong RAM của thread
 * solve. Nếu callback tới Entry thất bại, hàng chục phút tính toán mất trắng — đúng
 * kịch bản đã xảy ra với job #21 (Entry trả 500 do tràn cột {@code total_cost}).
 *
 * <p>Lớp này ghi payload xuống đĩa <b>trước</b> khi gọi callback, nên kết quả luôn
 * phát lại được, kể cả sau khi engine restart.
 *
 * <pre>
 *   .data/results/
 *     pending/  job-N.json.gz + job-N.meta.json   — chưa giao được, sweeper sẽ retry
 *     sent/                                        — Entry đã nhận (2xx)
 *     poison/                                      — Entry từ chối (4xx) hoặc hết lượt thử
 * </pre>
 */
@Slf4j
@Component
public class ResultSpool {

    /**
     * Sổ theo dõi số lần thử giao. Nằm cạnh payload (không nằm trong RAM) để sống
     * sót qua restart — nếu giữ trong bộ nhớ thì backoff sẽ reset mỗi lần khởi động.
     */
    public record Meta(int attempts, Instant lastAttemptAt, String lastError) {
        public static Meta initial() {
            return new Meta(0, null, null);
        }
    }

    private final JsonMapper objectMapper;
    private final Path pendingDir;
    private final Path sentDir;
    private final Path poisonDir;

    public ResultSpool(JsonMapper objectMapper,
                       @Value("${gvrp.result-spool.dir:./.data/results}") String baseDir) {
        this.objectMapper = objectMapper;
        Path base = Paths.get(baseDir);
        this.pendingDir = base.resolve("pending");
        this.sentDir = base.resolve("sent");
        this.poisonDir = base.resolve("poison");
        try {
            Files.createDirectories(pendingDir);
            Files.createDirectories(sentDir);
            Files.createDirectories(poisonDir);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Không tạo được thư mục spool " + base.toAbsolutePath(), e);
        }
        log.info("💾 Result spool tại {}", base.toAbsolutePath());
    }

    /**
     * Ghi payload xuống {@code pending/}.
     *
     * <p>Ghi ra {@code .tmp} rồi ATOMIC_MOVE: nếu process chết giữa chừng thì không
     * bao giờ để lại file JSON cụt mà sweeper tưởng là hợp lệ.
     *
     * <p>Cố tình <b>ném</b> exception khi ghi hỏng. Mất kết quả nghiêm trọng hơn
     * nhiều so với callback fail, nên không nuốt lặng lẽ như {@code deliver()}.
     */
    public void store(Long jobId, Map<String, Object> payload) {
        Path target = pendingDir.resolve(fileName(jobId));
        Path tmp = pendingDir.resolve(fileName(jobId) + ".tmp");
        try {
            try (OutputStream os = new GZIPOutputStream(Files.newOutputStream(
                    tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                objectMapper.writeValue(os, payload);
            }
            Files.move(tmp, target,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            writeMeta(pendingDir, jobId, Meta.initial());
        } catch (Exception e) {
            throw new IllegalStateException("Không lưu được kết quả job #" + jobId, e);
        }

        // Log NGOÀI khối try: tới đây payload đã nằm an toàn trên đĩa. Nếu Files.size()
        // nằm trong try và ném lỗi, store() sẽ ném theo và chặn luôn deliver() — hủy
        // một callback đáng lẽ gửi được, chỉ vì không đọc nổi kích thước file.
        try {
            log.info("💾 Đã lưu kết quả job #{} ({} KB) → {}",
                    jobId, Files.size(target) / 1024, target);
        } catch (Exception e) {
            log.info("💾 Đã lưu kết quả job #{} → {}", jobId, target);
        }
    }

    /**
     * Tìm theo thứ tự {@code pending → poison → sent}, để resend được cả job đã giao
     * thành công (ví dụ khi cần nạp lại vào DB sau sự cố).
     */
    public Optional<Map<String, Object>> load(Long jobId) {
        for (Path dir : List.of(pendingDir, poisonDir, sentDir)) {
            Path f = dir.resolve(fileName(jobId));
            if (Files.exists(f)) {
                try (InputStream is = new GZIPInputStream(Files.newInputStream(f))) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = objectMapper.readValue(is, Map.class);
                    return Optional.of(payload);
                } catch (Exception e) {
                    log.error("❌ Đọc hỏng {}: {}", f, e.getMessage());
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /** Danh sách jobId đang chờ giao, sắp xếp tăng dần. */
    public List<Long> listPending() {
        try (var s = Files.list(pendingDir)) {
            return s.map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith("job-") && n.endsWith(".json.gz"))
                    .map(n -> Long.parseLong(n.substring(4, n.length() - 8)))
                    .sorted()
                    .toList();
        } catch (Exception e) {
            log.error("❌ Không liệt kê được spool: {}", e.getMessage());
            return List.of();
        }
    }

    public Meta meta(Long jobId) {
        Path f = pendingDir.resolve(metaName(jobId));
        if (!Files.exists(f)) {
            return Meta.initial();
        }
        try (InputStream is = Files.newInputStream(f)) {
            return objectMapper.readValue(is, Meta.class);
        } catch (Exception e) {
            log.warn("⚠️ Đọc meta job #{} hỏng, coi như lần thử đầu: {}", jobId, e.getMessage());
            return Meta.initial();
        }
    }

    public void markSent(Long jobId) {
        move(pendingDir, sentDir, jobId);
        log.info("✅ Job #{} đã giao thành công → sent/", jobId);
    }

    public void markPoisoned(Long jobId, String reason) {
        Meta m = meta(jobId);
        writeMeta(pendingDir, jobId, new Meta(m.attempts(), Instant.now(), reason));
        move(pendingDir, poisonDir, jobId);
        log.error("💀 Job #{} → poison/, cần can thiệp tay. Lý do: {}", jobId, reason);
    }

    /**
     * Tăng bộ đếm lần thử.
     *
     * <p>Chỉ ghi meta khi payload thực sự còn nằm trong {@code pending/}. Endpoint
     * {@code /resend} có thể gọi {@code deliver()} cho job đã ở {@code sent/} hoặc
     * {@code poison/}; nếu ghi vô điều kiện sẽ đẻ ra file meta mồ côi trong
     * {@code pending/} mà {@link #listPending()} không bao giờ nhặt (nó chỉ lọc
     * {@code .json.gz}) và cũng không ai dọn.
     */
    public void recordFailure(Long jobId, String error) {
        if (!Files.exists(pendingDir.resolve(fileName(jobId)))) {
            return;
        }
        Meta m = meta(jobId);
        writeMeta(pendingDir, jobId, new Meta(m.attempts() + 1, Instant.now(), error));
    }

    private void writeMeta(Path dir, Long jobId, Meta meta) {
        Path f = dir.resolve(metaName(jobId));
        try (OutputStream os = Files.newOutputStream(
                f, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            objectMapper.writeValue(os, meta);
        } catch (Exception e) {
            log.warn("⚠️ Không ghi được meta job #{}: {}", jobId, e.getMessage());
        }
    }

    private void move(Path from, Path to, Long jobId) {
        for (String n : List.of(fileName(jobId), metaName(jobId))) {
            Path src = from.resolve(n);
            if (Files.exists(src)) {
                try {
                    Files.move(src, to.resolve(n), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    log.error("❌ Không chuyển được {} → {}: {}", src, to, e.getMessage());
                }
            }
        }
    }

    private static String fileName(Long jobId) {
        return "job-" + jobId + ".json.gz";
    }

    private static String metaName(Long jobId) {
        return "job-" + jobId + ".meta.json";
    }
}
