package org.truong.gvrp_engine_api.distance_matrix;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@Slf4j
@Service
public class DistanceMatrixService {

    private final DistanceProvider primaryProvider;

    public DistanceMatrixService(
            @Qualifier(value = "graphHoperDistanceProvider") DistanceProvider primaryProvider) {
        this.primaryProvider = primaryProvider;
    }

    /**
     * Create distance matrix for all coordinates
     */
    public DistanceMatrix createDistanceMatrix(List<OptCoordinates> coordinates) {
        int n = coordinates.size();
        log.info("Creating distance matrix for {} locations", coordinates.size());

        long startTime = System.currentTimeMillis();

        DistanceMatrixEntry[][] matrixArray = new DistanceMatrixEntry[n][n];
        AtomicInteger progressCounter = new AtomicInteger(0);
        int totalRows = n;

        IntStream.range(0, n).parallel().forEach(i -> {

            for (int j = 0; j < n; j++) {
                if (i == j) {
                    matrixArray[i][j] = DistanceMatrixEntry.ZERO;
                } else {
                    try {
                        matrixArray[i][j] = primaryProvider.fetch(coordinates.get(i), coordinates.get(j));
                    } catch (Exception e) {
                        log.error("Error route {}->{}", i, j);
                        matrixArray[i][j] = DistanceMatrixEntry.ZERO; // Fallback
                    }
                }
            }

            // Log tiến độ mỗi khi xong 10%
            int completed = progressCounter.incrementAndGet();
            if (completed % (totalRows / 10) == 0) {
                log.info("... Calculated {}/{} rows ({}%)", completed, totalRows, (completed * 100) / totalRows);
            }
        });

        Map<String, DistanceMatrixEntry> entries = new HashMap<>(n * n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                entries.put(i + "-" + j, matrixArray[i][j]);
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("✓ Distance matrix created in {} seconds ({} calculations)",
                elapsed / 1000.0, n * n);

        return new DistanceMatrix(coordinates, entries);
    }

}
