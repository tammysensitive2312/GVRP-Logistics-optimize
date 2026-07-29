package org.truong.gvrp_engine_api.distance_matrix;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chứng minh BlockDiagonalCostMatrix KHÔNG đổi giá trị so với ma trận dày, và
 * đối soát số học bộ nhớ.
 * <p>
 * Nguyên tắc: dùng {@link DenseCostMatrix} làm ORACLE. Điền hai bố cục bằng cùng
 * một nguồn dữ liệu, đúng theo tập ô mà {@link MatrixMask#needed} cho phép, rồi so
 * TỪNG Ô trong n². Nếu tập ô lưu của block lệch tập needed() dù chỉ một ô, test đổ.
 */
class BlockDiagonalCostMatrixTest {

    /** Sinh nhãn cụm: D depot đầu tiên, rồi các order chia đều vào C cụm. */
    private static int[] labels(int depots, int orders, int clusters) {
        int[] lab = new int[depots + orders];
        for (int i = 0; i < depots; i++) lab[i] = MatrixMask.DEPOT;
        for (int k = 0; k < orders; k++) lab[depots + k] = k % clusters;
        return lab;
    }

    /** Giá trị giả lập cho cặp (i,j) — deterministic, khác nhau theo cả i và j. */
    private static double dist(int i, int j) {
        return 1000.0 * i + j + 0.5;
    }

    private static double time(int i, int j) {
        return 7.0 * i + 3.0 * j + 0.25;
    }

    @Test
    @DisplayName("Block trả CÙNG giá trị với dense trên mọi ô needed(), và sentinel ở ô còn lại")
    void blockMatchesDenseCellByCell() {
        int depots = 3, orders = 60, clusters = 5;
        int[] lab = labels(depots, orders, clusters);
        int n = lab.length;

        MatrixMask mask = MatrixMask.forTesting(lab);
        DenseCostMatrix dense = new DenseCostMatrix(new double[n][n], new double[n][n]);
        BlockDiagonalCostMatrix block = BlockDiagonalCostMatrix.allocate(lab);

        // Điền dense theo ĐÚNG hợp đồng của DistanceMatrixService: ô không needed -> sentinel
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    dense.put(i, j, 0.0, 0.0);
                } else if (mask.needed(i, j)) {
                    dense.put(i, j, dist(i, j), time(i, j));
                } else {
                    dense.put(i, j, MatrixMask.PRUNED_METERS, MatrixMask.PRUNED_SECONDS);
                }
            }
        }

        // Điền block CHỈ qua targetsFor() — đúng như service làm
        for (int i = 0; i < n; i++) {
            for (int j : block.targetsFor(i)) {
                if (i == j) continue;
                block.put(i, j, dist(i, j), time(i, j));
            }
        }

        // So từng ô trong n²
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                assertEquals(dense.distanceMeters(i, j), block.distanceMeters(i, j), 0.0,
                        String.format("distance lệch tại (%d,%d) [cụm %d -> %d]", i, j, lab[i], lab[j]));
                assertEquals(dense.timeSeconds(i, j), block.timeSeconds(i, j), 0.0,
                        String.format("time lệch tại (%d,%d)", i, j));
            }
        }
    }

    @Test
    @DisplayName("targetsFor() phủ ĐÚNG tập needed() — không thiếu, không thừa ô có chỗ lưu")
    void targetsMatchMaskExactly() {
        int[] lab = labels(2, 40, 4);
        int n = lab.length;
        MatrixMask mask = MatrixMask.forTesting(lab);
        BlockDiagonalCostMatrix block = BlockDiagonalCostMatrix.allocate(lab);

        for (int i = 0; i < n; i++) {
            boolean[] inTargets = new boolean[n];
            for (int j : block.targetsFor(i)) inTargets[j] = true;

            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                assertEquals(mask.needed(i, j), inTargets[j], String.format(
                        "Ô (%d,%d): mask.needed=%b nhưng targetsFor=%b — bố cục block lệch mask",
                        i, j, mask.needed(i, j), inTargets[j]));
            }
        }
    }

    @Test
    @DisplayName("Ghi song song theo hàng an toàn: kết quả trùng bản tuần tự")
    void parallelFillIsRaceFree() {
        int[] lab = labels(4, 300, 10);
        int n = lab.length;

        BlockDiagonalCostMatrix seq = BlockDiagonalCostMatrix.allocate(lab);
        for (int i = 0; i < n; i++) {
            for (int j : seq.targetsFor(i)) {
                if (i != j) seq.put(i, j, dist(i, j), time(i, j));
            }
        }

        BlockDiagonalCostMatrix par = BlockDiagonalCostMatrix.allocate(lab);
        java.util.stream.IntStream.range(0, n).parallel().forEach(i -> {
            for (int j : par.targetsFor(i)) {
                if (i != j) par.put(i, j, dist(i, j), time(i, j));
            }
        });

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                assertEquals(seq.distanceMeters(i, j), par.distanceMeters(i, j), 0.0,
                        String.format("race tại (%d,%d)", i, j));
                assertEquals(seq.timeSeconds(i, j), par.timeSeconds(i, j), 0.0,
                        String.format("race tại (%d,%d)", i, j));
            }
        }
    }

    @Test
    @DisplayName("Cặp khác cụm trả sentinel, KHÔNG throw (NoPrunedEdgeConstraint cần đọc được)")
    void crossClusterReturnsSentinelNotException() {
        int[] lab = labels(1, 20, 2);
        BlockDiagonalCostMatrix block = BlockDiagonalCostMatrix.allocate(lab);

        // index 1 thuộc cụm 0, index 2 thuộc cụm 1 (vì lab[1+k] = k % 2)
        assertEquals(0, lab[1]);
        assertEquals(1, lab[2]);
        assertDoesNotThrow(() -> block.distanceMeters(1, 2));
        assertEquals(MatrixMask.PRUNED_METERS, block.distanceMeters(1, 2), 0.0);
        assertEquals(MatrixMask.PRUNED_SECONDS, block.timeSeconds(1, 2), 0.0);
    }

    @Test
    @DisplayName("allocatedBytes() của instance thật khớp công thức 8·(2·ΣS_c² + 4·W·n)")
    void allocatedBytesMatchesFormula() {
        int depots = 4, orders = 3_000, clusters = 20;
        int[] lab = labels(depots, orders, clusters);
        int n = lab.length;

        BlockDiagonalCostMatrix block = BlockDiagonalCostMatrix.allocate(lab);

        assertEquals(clusters, block.clusterCount());
        assertEquals(depots, block.wideCount());
        assertEquals(MatrixMemory.blockBytes(n, block.clusterSizes(), depots),
                block.allocatedBytes());
    }

    /**
     * Số học quy mô 50k — thuần công thức, KHÔNG cấp phát (chính vì cấp phát dày
     * ở quy mô này là thứ đang làm job treo).
     */
    @Test
    @DisplayName("Quy mô job 50k: dense 37 GiB vs block 136 MB, và block TUYẾN TÍNH theo n")
    void scaleArithmeticFor50kOrders() {
        int depots = 10, orders = 50_000, s = 150;
        int n = orders + depots;
        int[] sizes = evenClusterSizes(orders, s);

        long denseBytes = MatrixMemory.denseBytes(n);
        long blockBytes = MatrixMemory.blockBytes(n, sizes, depots);

        assertTrue(denseBytes > 30L * (1L << 30),
                "dense phải > 30 GiB, thực tế " + MatrixMemory.humanBytes(denseBytes));
        assertTrue(blockBytes < 200L * 1_000_000L,
                "block phải < 200 MB, thực tế " + MatrixMemory.humanBytes(blockBytes));
        assertTrue(denseBytes / blockBytes > 200,
                "phải giảm > 200×, thực tế " + (denseBytes / blockBytes) + "×");

        // TUYẾN TÍNH: gấp đôi số order thì bộ nhớ gấp ~2 (không phải ~4 như O(n²)).
        int n2 = 2 * orders + depots;
        long blockBytes2 = MatrixMemory.blockBytes(n2, evenClusterSizes(2 * orders, s), depots);
        double blockGrowth = blockBytes2 / (double) blockBytes;
        double denseGrowth = MatrixMemory.denseBytes(n2) / (double) denseBytes;

        assertTrue(blockGrowth < 2.2, "block phải tăng ~2×, thực tế " + blockGrowth);
        assertTrue(denseGrowth > 3.8, "dense phải tăng ~4×, thực tế " + denseGrowth);

        System.out.printf("n=%d | dense=%s | block=%s | giảm %d× | khi 2n: block ×%.2f, dense ×%.2f%n",
                n, MatrixMemory.humanBytes(denseBytes), MatrixMemory.humanBytes(blockBytes),
                denseBytes / blockBytes, blockGrowth, denseGrowth);
    }

    /** Chia orders vào các cụm kích thước ~s, giống công thức C = ceil(N/S_target). */
    private static int[] evenClusterSizes(int orders, int s) {
        int c = (int) Math.ceil(orders / (double) s);
        int[] sizes = new int[c];
        for (int k = 0; k < orders; k++) sizes[k % c]++;
        return sizes;
    }

    @Test
    @DisplayName("Guard trần dense nêu đúng n_max = sqrt(heap·0.6/16)")
    void denseGuardReportsCeiling() {
        long heap8g = 8L * (1L << 30);
        assertEquals(17947, MatrixMemory.maxDenseSize(heap8g));
        assertEquals(25381, MatrixMemory.maxDenseSize(16L * (1L << 30)));

        // n=50 010 phải vượt trần trên mọi heap local hợp lý
        assertTrue(MatrixMemory.denseBytes(50_010) > heap8g * MatrixMemory.HEAP_SAFETY_FRACTION);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> MatrixMemory.requireDenseFits(2_000_000));
        assertTrue(ex.getMessage().contains("KHÔNG phải rò rỉ"),
                "guard phải nói rõ đây là trần O(n²), không phải leak: " + ex.getMessage());
    }

    @Test
    @DisplayName("Dải rộng quá lớn (order sót cụm) bị chặn fail-loud với con số cụ thể")
    void wideBandGuardFires() {
        // 40 000 order KHÔNG gán cụm -> dải rộng 40 000 × n × 4 × 8 byte = hàng chục GiB
        int n = 50_000;
        int[] lab = new int[n];
        for (int i = 0; i < 40_000; i++) lab[i] = MatrixMask.UNCLUSTERED;
        for (int i = 40_000; i < n; i++) lab[i] = i % 100;

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> BlockDiagonalCostMatrix.allocate(lab));
        assertTrue(ex.getMessage().contains("CHƯA GÁN CỤM"), ex.getMessage());
        assertTrue(ex.getMessage().contains("VehicleClusterAssigner"),
                "guard phải chỉ đúng chỗ cần sửa, không phải bảo tăng -Xmx: " + ex.getMessage());
    }

    @Test
    @DisplayName("Guard tràn int: cụm > 46 340 phần tử bị chặn, không quấn vòng offset im lặng")
    void intOverflowGuardFires() {
        int big = 50_000;                       // 50 000² = 2.5e9 > Integer.MAX_VALUE
        int[] lab = new int[big + 1];
        lab[0] = MatrixMask.DEPOT;
        for (int i = 1; i <= big; i++) lab[i] = 0;   // TẤT CẢ vào một cụm

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> BlockDiagonalCostMatrix.allocate(lab));
        assertTrue(ex.getMessage().contains("Integer.MAX_VALUE"), ex.getMessage());
        assertTrue(ex.getMessage().contains("46340"),
                "guard phải nêu trần sqrt(Integer.MAX_VALUE)=46340: " + ex.getMessage());
    }

    @Test
    @DisplayName("Dense ctor chặn ma trận không vuông ngay, không để nổ muộn trong solver")
    void denseRejectsNonSquare() {
        double[][] d = new double[3][3];
        double[][] t = new double[3][];
        t[0] = new double[3];
        t[1] = new double[2];   // hàng lệch
        t[2] = new double[3];
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new DenseCostMatrix(d, t));
        assertTrue(ex.getMessage().contains("không vuông"), ex.getMessage());
    }

    @Test
    @DisplayName("Order chưa gán cụm (ít) vẫn đọc/ghi đúng như dense")
    void unclusteredOrdersBehaveLikeDepots() {
        int n = 30;
        int[] lab = new int[n];
        lab[0] = MatrixMask.DEPOT;
        lab[1] = MatrixMask.UNCLUSTERED;
        for (int i = 2; i < n; i++) lab[i] = i % 3;

        MatrixMask mask = MatrixMask.forTesting(lab);
        BlockDiagonalCostMatrix block = BlockDiagonalCostMatrix.allocate(lab);
        for (int i = 0; i < n; i++) {
            for (int j : block.targetsFor(i)) {
                if (i != j) block.put(i, j, dist(i, j), time(i, j));
            }
        }

        for (int j = 0; j < n; j++) {
            if (j == 1) continue;
            assertEquals(dist(1, j), block.distanceMeters(1, j), 0.0, "hàng UNCLUSTERED sai tại j=" + j);
            assertEquals(dist(j, 1), block.distanceMeters(j, 1), 0.0, "cột UNCLUSTERED sai tại i=" + j);
            assertTrue(mask.needed(1, j) && mask.needed(j, 1));
        }
    }
}
