package org.truong.gvrp_engine_api.distance_matrix;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
        log.info("Creating distance matrix for {} locations", coordinates.size());

        DistanceProvider provider = primaryProvider;
        long startTime = System.currentTimeMillis();

        Map<String, DistanceMatrixEntry> entries = new HashMap<>();
        int n = coordinates.size();

        // Create all tasks
        List<CompletableFuture<Void>> futures = IntStream.range(0, n)
                .boxed()
                .flatMap(i -> IntStream.range(0, n)
                        .mapToObj(j -> calculateEntry(coordinates, i, j, provider, entries)))
                .toList();

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("✓ Distance matrix created in {} seconds ({} calculations)",
                elapsed / 1000.0, n * n);

        return new DistanceMatrix(coordinates, entries);
    }

    private CompletableFuture<Void> calculateEntry(
            List<OptCoordinates> coordinates,
            int i,
            int j,
            DistanceProvider provider,
            Map<String, DistanceMatrixEntry> entries) {

        return CompletableFuture.runAsync(() -> {
            try {
                OptCoordinates from = coordinates.get(i);
                OptCoordinates to = coordinates.get(j);

                DistanceMatrixEntry entry;

                if (i == j) {
                    // Same location = zero distance
                    entry = DistanceMatrixEntry.ZERO;
                } else {
                    // Calculate using provider
                    entry = provider.fetch(from, to);
                }

                String key = i + "-" + j;
                synchronized (entries) {
                    entries.put(key, entry);
                }

            } catch (Exception e) {
                log.error("Error calculating distance for [{}, {}]", i, j, e);

                // Use fallback
                String key = i + "-" + j;
                synchronized (entries) {
                    entries.put(key, DistanceMatrixEntry.ZERO);
                }
            }
        });
    }
}
