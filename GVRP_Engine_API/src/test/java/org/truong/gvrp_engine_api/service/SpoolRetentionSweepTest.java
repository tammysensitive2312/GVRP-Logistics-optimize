package org.truong.gvrp_engine_api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Retention sweep cho spool: sent/poison quá hạn thì xóa (kèm meta),
 * pending/ không bao giờ bị đụng.
 */
class SpoolRetentionSweepTest {

    @TempDir
    Path tempDir;

    private ResultSpool spool;

    @BeforeEach
    void setUp() {
        spool = new ResultSpool(
                new JsonMapper(), tempDir.resolve("results").toString());
    }

    @Test
    void sweep_sentOlderThanRetention_removesPayloadAndMeta() throws Exception {
        spool.store(1L, Map.of("job_id", 1L));
        spool.store(2L, Map.of("job_id", 2L));
        spool.markSent(1L);
        spool.markSent(2L);
        backdate("sent", 1L, 10);

        int removed = spool.sweepDelivered(Duration.ofDays(7), Duration.ofDays(30));

        assertEquals(1, removed);
        assertFalse(Files.exists(sentDir().resolve("job-1.json.gz")));
        assertFalse(Files.exists(sentDir().resolve("job-1.meta.json")));
        assertTrue(Files.exists(sentDir().resolve("job-2.json.gz")));
    }

    @Test
    void sweep_freshSentAndYoungPoison_areKept() throws Exception {
        spool.store(3L, Map.of("job_id", 3L));
        spool.markSent(3L);
        spool.store(4L, Map.of("job_id", 4L));
        spool.markPoisoned(4L, "4xx");
        backdate("poison", 4L, 10);

        int removed = spool.sweepDelivered(Duration.ofDays(7), Duration.ofDays(30));

        assertEquals(0, removed);
        assertTrue(Files.exists(sentDir().resolve("job-3.json.gz")));
        assertTrue(Files.exists(poisonDir().resolve("job-4.json.gz")));
    }

    @Test
    void sweep_oldPoisonBeyondRetention_isRemoved() throws Exception {
        spool.store(5L, Map.of("job_id", 5L));
        spool.markPoisoned(5L, "4xx");
        backdate("poison", 5L, 40);

        int removed = spool.sweepDelivered(Duration.ofDays(7), Duration.ofDays(30));

        assertEquals(1, removed);
        assertFalse(Files.exists(poisonDir().resolve("job-5.json.gz")));
        assertFalse(Files.exists(poisonDir().resolve("job-5.meta.json")));
    }

    @Test
    void sweep_neverTouchesPending() throws Exception {
        spool.store(6L, Map.of("job_id", 6L));
        backdate("pending", 6L, 40);

        int removed = spool.sweepDelivered(Duration.ofDays(7), Duration.ofDays(30));

        assertEquals(0, removed);
        assertEquals(List.of(6L), spool.listPending());
    }

    private Path sentDir() {
        return tempDir.resolve("results/sent");
    }

    private Path poisonDir() {
        return tempDir.resolve("results/poison");
    }

    private void backdate(String dir, long jobId, int daysAgo) throws Exception {
        FileTime past = FileTime.from(Instant.now().minus(Duration.ofDays(daysAgo)));
        Path base = tempDir.resolve("results/" + dir);
        Files.setLastModifiedTime(base.resolve("job-" + jobId + ".json.gz"), past);
        Files.setLastModifiedTime(base.resolve("job-" + jobId + ".meta.json"), past);
    }
}
