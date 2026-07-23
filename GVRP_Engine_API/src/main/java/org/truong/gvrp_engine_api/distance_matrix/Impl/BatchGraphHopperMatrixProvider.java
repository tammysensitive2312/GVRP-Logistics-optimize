package org.truong.gvrp_engine_api.distance_matrix.Impl;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.DijkstraOneToMany;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.PMap;
import lombok.extern.slf4j.Slf4j;
import org.truong.gvrp_engine_api.distance_matrix.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Dựng cả ma trận bằng one-to-many (DijkstraOneToMany trên base graph GH 11).
 * - Snap toàn bộ điểm 1 lần, build QueryGraph 1 lần.
 * - Mỗi source: 1 lần clear() rồi calcPath tới từng target CẦN (theo mask).
 * ĐÁNH ĐỔI: node-based (bỏ turn cost). Chỉ hiệu quả khi target cục bộ (ghép cluster-block + weightLimit).
 */
@Slf4j
public class BatchGraphHopperMatrixProvider {

    private final GraphHopper hopper;
    private final String profileName;

    public BatchGraphHopperMatrixProvider(GraphHopper hopper, String profileName) {
        this.hopper = hopper;
        this.profileName = profileName;
    }

    public DistanceMatrixEntry[][] computeMatrix(List<OptCoordinates> coords, MatrixMask mask, Double weightLimit) {
        int n = coords.size();
        BaseGraph baseGraph = hopper.getBaseGraph();
        LocationIndex index = hopper.getLocationIndex();
        Profile profile = hopper.getProfile(profileName);

        // Weighting bỏ turn cost (node-based) — cho cả snap filter lẫn routing.
        Weighting weighting = hopper.createWeighting(profile, new PMap(), true);
        EdgeFilter snapFilter = new DefaultSnapFilter(
                weighting,
                hopper.getEncodingManager().getBooleanEncodedValue(Subnetwork.key(profileName)));

        // 1) Snap tất cả điểm 1 lần
        List<Snap> snaps = new ArrayList<>(n);
        for (OptCoordinates c : coords) {
            Snap s = index.findClosest(c.latDouble(), c.lonDouble(), snapFilter);
            if (!s.isValid()) {
                throw new IllegalStateException("Không snap được điểm " + c.latDouble() + "," + c.lonDouble());
            }
            snaps.add(s);
        }

        // 2) QueryGraph 1 lần (virtual node cho mọi điểm) + wrap weighting theo query graph
        QueryGraph queryGraph = QueryGraph.create(baseGraph, snaps);
        Weighting qWeighting = queryGraph.wrapWeighting(weighting);

        DistanceMatrixEntry[][] m = new DistanceMatrixEntry[n][n];
        DijkstraOneToMany algo = new DijkstraOneToMany(queryGraph, qWeighting, TraversalMode.NODE_BASED);
        if (weightLimit != null) algo.setWeightLimit(weightLimit); // cắt sớm khi target chỉ trong bán kính cụm

        long calls = 0;
        for (int i = 0; i < n; i++) {
            algo.clear();                              // đổi source -> phải clear cache
            int fromNode = snaps.get(i).getClosestNode();
            for (int j = 0; j < n; j++) {
                if (i == j) { m[i][j] = DistanceMatrixEntry.ZERO; continue; }
                if (mask != null && !mask.needed(i, j)) {
                    m[i][j] = sentinel(); continue;
                }
                int toNode = snaps.get(j).getClosestNode();
                Path p = algo.calcPath(fromNode, toNode); // reuse cây đường đi của source i
                calls++;
                m[i][j] = p.isFound()
                        ? new DistanceMatrixEntry(Duration.ofMillis(p.getTime()), Distance.ofMeters(p.getDistance()))
                        : sentinel();
            }
        }
        log.info("One-to-many xong: {} calcPath (thay vì {} route CH)", calls, (long) n * (n - 1));
        return m;
    }

    private static DistanceMatrixEntry sentinel() {
        return new DistanceMatrixEntry(
                Duration.ofSeconds((long) MatrixMask.PRUNED_SECONDS),
                Distance.ofMeters(MatrixMask.PRUNED_METERS));
    }
}