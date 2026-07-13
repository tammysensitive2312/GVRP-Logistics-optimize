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
import java.util.*;

/**
 * Circuity Factor Measurement
 * <p>
 * ⚠️ IMPORTANT — DATA SOURCE DISCLAIMER:
 * This test currently runs on SYNTHETIC data (uniform random points around
 * a center coordinate). This is intentional for a first dry-run to validate
 * the measurement pipeline (Haversine math, bucket grouping, CSV export,
 * GraphHopper integration) — NOT to derive a production threshold.
 * <p>
 * Uniform random points do NOT reflect Hanoi's real topology (Red River
 * crossings, one-way streets in the Old Quarter, road_access=DESTINATION
 * restrictions). Circuity factors measured here will likely be smoother
 * and lower than reality, because real detours caused by bridges/one-way
 * networks are absent from this distribution.
 * <p>
 * TODO: Once validated, re-run with real order/depot coordinates from the
 * production DB (see plan to swap {@link #buildSyntheticCoordinates()} for
 * a DB-backed coordinate loader).
 * <p>
 * Output: both a summary table (log) AND a full per-sample CSV, since
 * mean/stddev alone can hide multi-modal effects (e.g. a subset of
 * short-distance pairs with abnormally high ratio due to bridge detours).
 */
@SpringBootTest
public class CircuityFactorMeasurementTest {
    private static final Logger log = LoggerFactory.getLogger(CircuityFactorMeasurementTest.class);

    private static final boolean SYNTHETIC_DATA = true; // flip to false once DB-backed loader is wired in

    private static final int NUM_ORDERS = 1000;
    private static final long RANDOM_SEED = 42L;

    private static final double CENTER_LAT = 21.0285;
    private static final double CENTER_LON = 105.8542;
    private static final double SPREAD_DEGREES = 0.05;

    private static List<OptCoordinates> sharedCoordinates;
    private static double[][] sharedDistanceMatrix;

    // Target samples PER BUCKET, not total — this is the core fix for
    // stratified sampling. Total sample count now varies with number of
    // buckets actually populated, but each bucket gets a guaranteed floor.
    private static final int SAMPLES_PER_BUCKET = 500;
    private static final int BUCKET_SIZE_METERS = 500;
    private static final int MAX_BUCKET_METERS = 10_000; // beyond this, lump into one overflow bucket

    // Safety valve for the sampling loop — without this, a region with many
    // ZERO-distance fallback entries (GraphHopper routing failures) could
    // spin close to forever trying to fill a bucket that can never be filled.
    private static final int MAX_ATTEMPTS_PER_BUCKET = SAMPLES_PER_BUCKET * 50;

    @Autowired
    private DistanceMatrixService distanceMatrixService;

    private void ensureSharedMatrixBuilt() {
        if (sharedDistanceMatrix != null) {
            return;
        }

        log.info("🗺️  Building shared distance matrix via REAL GraphHopper ({} locations)...",
                NUM_ORDERS + 1);
        if (SYNTHETIC_DATA) {
            log.warn("⚠️  SYNTHETIC DATA MODE — coordinates are uniform-random, NOT real orders. " +
                    "Do not use the resulting threshold for production decisions.");
        }

        List<OptCoordinates> coordinates = SYNTHETIC_DATA
                ? buildSyntheticCoordinates()
                : loadCoordinatesFromDatabase();

        long start = System.currentTimeMillis();
        DistanceMatrix ghMatrix = distanceMatrixService.createDistanceMatrix(coordinates);
        long elapsed = System.currentTimeMillis() - start;

        int n = coordinates.size();
        double[][] distanceMatrix = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                DistanceMatrixEntry entry = ghMatrix.get(i, j);
                distanceMatrix[i][j] = entry.distanceMeters();
            }
        }

        sharedCoordinates = coordinates;
        sharedDistanceMatrix = distanceMatrix;

        log.info("✅ Shared distance matrix built in {} ms ({} locations, {} cells)",
                elapsed, n, (long) n * n);
    }

    /**
     * SYNTHETIC data source — uniform random points around Hanoi center.
     * See class-level disclaimer: does not reflect real topology.
     */
    private List<OptCoordinates> buildSyntheticCoordinates() {
        Random random = new Random(RANDOM_SEED);
        List<OptCoordinates> coordinates = new ArrayList<>();

        coordinates.add(new OptCoordinates(
                BigDecimal.valueOf(CENTER_LAT),
                BigDecimal.valueOf(CENTER_LON)
        ));

        for (int i = 0; i < NUM_ORDERS; i++) {
            double lat = CENTER_LAT + (random.nextDouble() - 0.5) * 2 * SPREAD_DEGREES;
            double lon = CENTER_LON + (random.nextDouble() - 0.5) * 2 * SPREAD_DEGREES;
            coordinates.add(new OptCoordinates(BigDecimal.valueOf(lat), BigDecimal.valueOf(lon)));
        }

        return coordinates;
    }

    /**
     * TODO: wire this to the real orders/depots repository once ready to
     * move off synthetic data. Should pull actual lat/lon from production
     * (or a representative snapshot) so circuity factors reflect real
     * river crossings, one-way streets, and road_access restrictions.
     */
    private List<OptCoordinates> loadCoordinatesFromDatabase() {
        throw new UnsupportedOperationException(
                "DB-backed coordinate loading not implemented yet. " +
                        "Set SYNTHETIC_DATA = true for now, or implement this method.");
    }

    /**
     * Stratified sampling: instead of drawing globally-random pairs and
     * grouping afterward (which starves near-distance buckets, since random
     * uniform points naturally produce far more far-distance pairs by simple
     * geometry — area of an annulus grows with radius), we fill each
     * distance bucket independently until it reaches its target count or
     * exhausts its attempt budget.
     */
    private List<CircuitySample> buildCircuitySamples() {

        Random random = new Random(RANDOM_SEED);
        int n = sharedCoordinates.size();

        int numBuckets = (MAX_BUCKET_METERS / BUCKET_SIZE_METERS) + 1; // +1 for overflow bucket
        List<CircuitySample> allSamples = new ArrayList<>();

        for (int bucketIndex = 0; bucketIndex < numBuckets; bucketIndex++) {
            boolean isOverflowBucket = (bucketIndex == numBuckets - 1);
            double bucketMin = bucketIndex * (double) BUCKET_SIZE_METERS;
            double bucketMax = isOverflowBucket
                    ? Double.MAX_VALUE
                    : bucketMin + BUCKET_SIZE_METERS;

            List<CircuitySample> bucketSamples = new ArrayList<>(SAMPLES_PER_BUCKET);
            int attempts = 0;

            while (bucketSamples.size() < SAMPLES_PER_BUCKET
                    && attempts < MAX_ATTEMPTS_PER_BUCKET) {

                attempts++;

                int from = random.nextInt(n);
                int to = random.nextInt(n);
                if (from == to) {
                    continue;
                }

                double euclidean = GeoUtils.haversine(
                        sharedCoordinates.get(from),
                        sharedCoordinates.get(to));

                if (euclidean < 1.0) {
                    continue;
                }
                if (euclidean < bucketMin || euclidean >= bucketMax) {
                    continue; // belongs to a different bucket, skip
                }

                double road = sharedDistanceMatrix[from][to];
                if (road <= 0) {
                    continue; // GraphHopper fallback ZERO entry, unusable
                }

                double ratio = road / euclidean;
                bucketSamples.add(new CircuitySample(from, to, euclidean, road, ratio));
            }

            if (bucketSamples.isEmpty() && attempts >= MAX_ATTEMPTS_PER_BUCKET) {
                log.debug("Bucket [{}-{}) yielded 0 samples after {} attempts — likely no pairs " +
                                "exist in this distance range for the current coordinate set.",
                        (long) bucketMin, isOverflowBucket ? "∞" : String.valueOf((long) bucketMax), attempts);
                continue;
            }

            if (bucketSamples.size() < SAMPLES_PER_BUCKET) {
                log.warn("⚠️  Bucket [{}-{}) only reached {}/{} samples after {} attempts " +
                                "(budget exhausted). Statistics for this bucket may be less reliable.",
                        (long) bucketMin, isOverflowBucket ? "∞" : String.valueOf((long) bucketMax),
                        bucketSamples.size(), SAMPLES_PER_BUCKET, attempts);
            }

            allSamples.addAll(bucketSamples);
        }

        log.info("Collected {} circuity samples across {} buckets (stratified).",
                allSamples.size(), numBuckets);

        return allSamples;
    }

    private Map<Integer, List<CircuitySample>> groupByDistanceBucket(
            List<CircuitySample> samples) {

        Map<Integer, List<CircuitySample>> buckets = new TreeMap<>();

        for (CircuitySample sample : samples) {
            int bucket = (int) (sample.euclideanDistance() / BUCKET_SIZE_METERS);
            buckets.computeIfAbsent(bucket, k -> new ArrayList<>()).add(sample);
        }

        return buckets;
    }

    private List<BucketStatistics> calculateStatistics(
            Map<Integer, List<CircuitySample>> buckets) {

        List<BucketStatistics> result = new ArrayList<>();

        for (Map.Entry<Integer, List<CircuitySample>> entry : buckets.entrySet()) {

            List<CircuitySample> list = entry.getValue();

            double sum = 0;
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;

            for (CircuitySample sample : list) {
                sum += sample.ratio();
                min = Math.min(min, sample.ratio());
                max = Math.max(max, sample.ratio());
            }

            double average = sum / list.size();

            double variance = 0;
            for (CircuitySample sample : list) {
                double diff = sample.ratio() - average;
                variance += diff * diff;
            }
            variance /= list.size();

            double std = Math.sqrt(variance);

            result.add(new BucketStatistics(
                    entry.getKey(),
                    list.size(),
                    average,
                    std,
                    min,
                    max
            ));
        }

        return result;
    }

    private void printStatistics(List<BucketStatistics> statistics) {

        log.info("");
        log.info("==============================================================================");
        log.info("Range(m)          Samples   AvgRatio   StdDev     Min      Max");
        log.info("==============================================================================");

        for (BucketStatistics stat : statistics) {

            int from = stat.bucketIndex() * BUCKET_SIZE_METERS;
            int to = from + BUCKET_SIZE_METERS;

            log.info("{}-{}\t{}\t{}\t{}\t{}\t{}",
                    from,
                    to,
                    stat.sampleCount(),
                    String.format(Locale.US, "%.3f", stat.averageRatio()),
                    String.format(Locale.US, "%.3f", stat.stdDeviation()),
                    String.format(Locale.US, "%.3f", stat.minRatio()),
                    String.format(Locale.US, "%.3f", stat.maxRatio()));
        }

        log.info("==============================================================================");
        if (SYNTHETIC_DATA) {
            log.warn("⚠️  Above statistics are from SYNTHETIC data. Do not derive production " +
                    "threshold from this run — re-run with SYNTHETIC_DATA=false once DB loader is ready.");
        }
    }

    /**
     * Exports every individual sample (not just bucket aggregates) so the
     * distribution can be plotted later (scatter / histogram). Aggregates
     * alone (mean/stddev) can hide multi-modal effects, e.g. a cluster of
     * short-distance pairs with abnormally high ratio due to bridge detours.
     */
    private void exportSamplesToCsv(List<CircuitySample> samples) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String dataLabel = SYNTHETIC_DATA ? "synthetic" : "real";
        String fileName = String.format("circuity_samples_%s_%s.csv", dataLabel, timestamp);

        Path outputDir = Paths.get("build", "circuity-reports");
        Path outputPath = outputDir.resolve(fileName);

        try {
            Files.createDirectories(outputDir);

            try (FileWriter writer = new FileWriter(outputPath.toFile())) {
                writer.write("from_index,to_index,euclidean_m,road_m,ratio\n");
                for (CircuitySample s : samples) {
                    writer.write(String.format(Locale.US, "%d,%d,%.2f,%.2f,%.4f%n",
                            s.fromIndex(), s.toIndex(), s.euclideanDistance(),
                            s.roadDistance(), s.ratio()));
                }
            }

            log.info("📄 Exported {} samples to {}", samples.size(), outputPath.toAbsolutePath());

        } catch (IOException e) {
            // Non-fatal: CSV export failing should not fail the measurement run,
            // but must be visible — silent data loss here would be worse than a
            // loud warning, since the whole point of this test is the exported data.
            log.error("❌ Failed to export circuity samples to CSV: {}", e.getMessage(), e);
        }
    }

    @Test
    void measureCircuityFactor() {
        ensureSharedMatrixBuilt();
        List<CircuitySample> samples = buildCircuitySamples();

        if (samples.isEmpty()) {
            log.error("❌ No circuity samples collected — cannot compute statistics. " +
                    "Check GraphHopper is returning valid routes (non-ZERO fallback).");
            return;
        }

        Map<Integer, List<CircuitySample>> buckets = groupByDistanceBucket(samples);
        List<BucketStatistics> statistics = calculateStatistics(buckets);

        printStatistics(statistics);
        exportSamplesToCsv(samples);
    }

    public record CircuitySample(
            int fromIndex,
            int toIndex,
            double euclideanDistance,
            double roadDistance,
            double ratio
    ) {
    }

    private record BucketStatistics(
            int bucketIndex,
            int sampleCount,
            double averageRatio,
            double stdDeviation,
            double minRatio,
            double maxRatio
    ) {
    }
}