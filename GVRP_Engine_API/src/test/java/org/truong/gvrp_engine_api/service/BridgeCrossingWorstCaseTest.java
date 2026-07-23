package org.truong.gvrp_engine_api.service;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.truong.gvrp_engine_api.distance_matrix.DistanceMatrix;
import org.truong.gvrp_engine_api.distance_matrix.DistanceMatrixEntry;
import org.truong.gvrp_engine_api.distance_matrix.DistanceMatrixService;
import org.truong.gvrp_engine_api.distance_matrix.OptCoordinates;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * WORST-CASE bridge-crossing circuity factor test.
 * <p>
 * PURPOSE: answer one narrow question as fast as possible —
 * "At what Euclidean distance does the road/Euclidean ratio for a real
 * river crossing (Long Bien <-> Hoan Kiem/Ba Dinh bank) drop to an
 * acceptable level?" — NOT to characterize the full distribution.
 * <p>
 * This is intentionally NOT random sampling and NOT stratified by cluster.
 * It directly grids two small bounding boxes on opposite riverbanks near
 * the bridges (Long Bien bridge / Chuong Duong bridge) and computes EVERY
 * cross-pair between them. Since both boxes are small (a few hundred
 * meters across) and adjacent to the same two bridges, this cross-pair
 * set IS the worst-case population: any pair that must cross the river
 * near here has no better option than these two bridges.
 * <p>
 * Reading the result: look at the ratio column for the SMALLEST Euclidean
 * distances (these are the pairs that would tempt a naive threshold into
 * skipping GraphHopper). If ratio is still e.g. 3-5x at 300-500m Euclidean,
 * that confirms a flat "300m works everywhere" threshold is unsafe for
 * Hanoi's river topology, and gives a concrete number to reason from.
 * <p>
 * Deliberately excluded from scope (see Method 4 / A-B test as follow-up):
 * - no anchor_id metadata, no cluster classification
 * - no statistical distribution, no stddev
 * - no changes to MillionOrderGenerator or CircuityFactorMeasurementTest
 */
@SpringBootTest
public class BridgeCrossingWorstCaseTest {

    private static final Logger log = LoggerFactory.getLogger(BridgeCrossingWorstCaseTest.class);

    // ==================== BOUNDING BOXES (provided by domain owner) ====================

    // Long Bien side (Ngoc Lam / Bo De ward), riverbank edge nearest the bridges
    private static final double LONG_BIEN_MIN_LAT = 21.0390;
    private static final double LONG_BIEN_MAX_LAT = 21.0460;
    private static final double LONG_BIEN_MIN_LON = 105.8650;
    private static final double LONG_BIEN_MAX_LON = 105.8730;

    // Hoan Kiem / Ba Dinh side (Phuc Tan / Chuong Duong Do), opposite riverbank edge
    private static final double SOUTH_BANK_MIN_LAT = 21.0310;
    private static final double SOUTH_BANK_MAX_LAT = 21.0380;
    private static final double SOUTH_BANK_MIN_LON = 105.8540;
    private static final double SOUTH_BANK_MAX_LON = 105.8620;

    // Grid resolution per box. 6x6 = 36 points per side -> 36*36 = 1296 cross-pairs.
    // Small enough to run in seconds against GraphHopper, large enough to see
    // the full range of Euclidean distances possible between the two boxes.
    private static final int GRID_POINTS_PER_AXIS = 6;

    @Autowired
    private DistanceMatrixService distanceMatrixService;

    @Test
    void measureWorstCaseBridgeCrossingRatio() {

        List<OptCoordinates> longBienPoints = buildGrid(
                LONG_BIEN_MIN_LAT, LONG_BIEN_MAX_LAT, LONG_BIEN_MIN_LON, LONG_BIEN_MAX_LON);
        List<OptCoordinates> southBankPoints = buildGrid(
                SOUTH_BANK_MIN_LAT, SOUTH_BANK_MAX_LAT, SOUTH_BANK_MIN_LON, SOUTH_BANK_MAX_LON);

        log.info("Grid sizes: Long Bien side={} points, South bank side={} points ({} cross-pairs)",
                longBienPoints.size(), southBankPoints.size(),
                longBienPoints.size() * southBankPoints.size());

        // Combine into one coordinate list so DistanceMatrixService (which builds
        // an NxN matrix) only has to compute the cross block we actually need,
        // by reading matrix.get(i, j) only for i in longBien range, j in southBank range.
        List<OptCoordinates> allPoints = new ArrayList<>(longBienPoints.size() + southBankPoints.size());
        allPoints.addAll(longBienPoints);
        allPoints.addAll(southBankPoints);

        int longBienCount = longBienPoints.size();

        long start = System.currentTimeMillis();
        DistanceMatrix matrix = distanceMatrixService.createDistanceMatrix(allPoints, null); // null = full matrix (không prune)
        long elapsed = System.currentTimeMillis() - start;
        log.info("✅ GraphHopper matrix built in {} ms ({} locations)", elapsed, allPoints.size());

        List<CrossPairSample> samples = new ArrayList<>(longBienCount * southBankPoints.size());

        for (int i = 0; i < longBienCount; i++) {
            for (int j = 0; j < southBankPoints.size(); j++) {
                int southIndex = longBienCount + j;

                OptCoordinates from = allPoints.get(i);
                OptCoordinates to = allPoints.get(southIndex);

                double euclidean = GeoUtils.haversine(from, to);

                DistanceMatrixEntry entry = matrix.get(i, southIndex);
                double road = entry.distanceMeters();

                if (road <= 0) {
                    // GraphHopper fallback ZERO — routing failure for this pair, skip
                    // but log it since a river-crossing routing failure is itself
                    // useful information (may indicate a real connectivity gap).
                    log.warn("⚠️  GraphHopper returned ZERO for pair ({},{}) -> ({},{}), skipping",
                            from.latDouble(), from.lonDouble(), to.latDouble(), to.lonDouble());
                    continue;
                }

                double ratio = road / euclidean;
                samples.add(new CrossPairSample(i, southIndex, euclidean, road, ratio));
            }
        }

        if (samples.isEmpty()) {
            log.error("❌ No valid cross-pair samples — cannot evaluate. Check GraphHopper routing for this area.");
            return;
        }

        // Sort by Euclidean distance ascending — the whole point is to inspect
        // ratio behavior at the SMALLEST distances first, since that's exactly
        // where a naive threshold would be tempted to skip GraphHopper.
        samples.sort((a, b) -> Double.compare(a.euclideanDistance(), b.euclideanDistance()));

        printTable(samples);
        exportCsv(samples);
    }

    /**
     * Simple uniform grid over a lat/lon bounding box.
     */
    private List<OptCoordinates> buildGrid(double minLat, double maxLat, double minLon, double maxLon) {
        List<OptCoordinates> points = new ArrayList<>();

        for (int i = 0; i < GRID_POINTS_PER_AXIS; i++) {
            for (int j = 0; j < GRID_POINTS_PER_AXIS; j++) {
                double latFraction = GRID_POINTS_PER_AXIS == 1 ? 0.5 : (double) i / (GRID_POINTS_PER_AXIS - 1);
                double lonFraction = GRID_POINTS_PER_AXIS == 1 ? 0.5 : (double) j / (GRID_POINTS_PER_AXIS - 1);

                double lat = minLat + latFraction * (maxLat - minLat);
                double lon = minLon + lonFraction * (maxLon - minLon);

                points.add(new OptCoordinates(BigDecimal.valueOf(lat), BigDecimal.valueOf(lon)));
            }
        }

        return points;
    }

    private void printTable(List<CrossPairSample> samples) {
        log.info("");
        log.info("==================================================================================");
        log.info("BRIDGE-CROSSING WORST-CASE SAMPLES (sorted by Euclidean distance, ascending)");
        log.info("==================================================================================");
        log.info("Euclidean(m)   Road(m)      Ratio");
        log.info("----------------------------------------------------------------------------------");

        for (CrossPairSample s : samples) {
            log.info("{}\t{}\t{}",
                    String.format(Locale.US, "%.1f", s.euclideanDistance()),
                    String.format(Locale.US, "%.1f", s.roadDistance()),
                    String.format(Locale.US, "%.3f", s.ratio()));
        }

        log.info("==================================================================================");

        // Quick headline numbers so the answer is visible without opening the CSV
        CrossPairSample closestPair = samples.get(0);
        double minRatio = samples.stream().mapToDouble(CrossPairSample::ratio).min().orElse(Double.NaN);
        double maxRatio = samples.stream().mapToDouble(CrossPairSample::ratio).max().orElse(Double.NaN);
        double avgRatio = samples.stream().mapToDouble(CrossPairSample::ratio).average().orElse(Double.NaN);

        log.info("Closest cross-river pair: Euclidean={} m, Road={} m, Ratio={}",
                String.format(Locale.US, "%.1f", closestPair.euclideanDistance()),
                String.format(Locale.US, "%.1f", closestPair.roadDistance()),
                String.format(Locale.US, "%.3f", closestPair.ratio()));
        log.info("Ratio across all {} cross-river samples: min={}, avg={}, max={}",
                samples.size(),
                String.format(Locale.US, "%.3f", minRatio),
                String.format(Locale.US, "%.3f", avgRatio),
                String.format(Locale.US, "%.3f", maxRatio));
        log.info("==================================================================================");
        log.info("👉 To pick τ: find the largest Euclidean distance in the table above where ratio is");
        log.info("   still unacceptable for your tolerance, and set τ below that. Any pair closer than");
        log.info("   τ but on opposite riverbanks must NOT be filtered by Euclidean distance alone —");
        log.info("   this data set is exactly that worst case.");
    }

    private void exportCsv(List<CrossPairSample> samples) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = String.format("bridge_crossing_worst_case_%s.csv", timestamp);

        Path outputDir = Paths.get("build", "circuity-reports");
        Path outputPath = outputDir.resolve(fileName);

        try {
            Files.createDirectories(outputDir);

            try (FileWriter writer = new FileWriter(outputPath.toFile())) {
                writer.write("from_index,to_index,euclidean_m,road_m,ratio\n");
                for (CrossPairSample s : samples) {
                    writer.write(String.format(Locale.US, "%d,%d,%.2f,%.2f,%.4f%n",
                            s.fromIndex(), s.toIndex(), s.euclideanDistance(),
                            s.roadDistance(), s.ratio()));
                }
            }

            log.info("📄 Exported {} cross-pair samples to {}", samples.size(), outputPath.toAbsolutePath());

        } catch (IOException e) {
            log.error("❌ Failed to export bridge-crossing samples to CSV: {}", e.getMessage(), e);
        }
    }

    public record CrossPairSample(
            int fromIndex,
            int toIndex,
            double euclideanDistance,
            double roadDistance,
            double ratio
    ) {
    }
}