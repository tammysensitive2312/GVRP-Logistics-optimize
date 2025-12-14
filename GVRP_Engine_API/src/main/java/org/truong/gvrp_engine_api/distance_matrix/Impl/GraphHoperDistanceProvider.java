package org.truong.gvrp_engine_api.distance_matrix.Impl;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.json.Statement;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.TurnCostsConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.truong.gvrp_engine_api.distance_matrix.Distance;
import org.truong.gvrp_engine_api.distance_matrix.DistanceMatrixEntry;
import org.truong.gvrp_engine_api.distance_matrix.DistanceProvider;
import org.truong.gvrp_engine_api.distance_matrix.OptCoordinates;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import static com.graphhopper.json.Statement.If;

@Slf4j
@Component
public class GraphHoperDistanceProvider implements DistanceProvider {
    @Value("${graphhopper.osm.file}")
    private String osmFilePath;

    @Value("${graphhopper.graph.location}")
    private String graphLocation;

//    @Value("${graphhopper.profile}")
//    private String profile;

    @Getter
    private GraphHopper graphHopper;

    @PostConstruct
    public void init() {
        try {
            log.info("========================================");
            log.info("Initializing GraphHopper Distance Provider");
            log.info("OSM File: {}", osmFilePath);
            log.info("Graph Location: {}", graphLocation);
            log.info("========================================");

            Path osmPath = Paths.get(osmFilePath);
            if (!Files.exists(osmPath)) {
                log.error("❌ OSM file not found: {}", osmFilePath);
                log.error("Please download Vietnam OSM data:");
                log.error("wget https://download.geofabrik.de/asia/vietnam-latest.osm.pbf -P .data/");
                return;
            }

            long fileSize = Files.size(osmPath) / (1024 * 1024); // MB
            log.info("OSM file found: {} MB", fileSize);

            // Create GraphHopper instance
            graphHopper = createGraphHopperInstance();

            log.info("✅ GraphHopper initialized successfully");

        } catch (Exception e) {
            log.error("❌ Failed to initialize GraphHopper", e);
        }
    }

    private GraphHopper createGraphHopperInstance() {
        log.info("Building routing graph... (this may take a few minutes on first run)");

        GraphHopper hopper = new GraphHopper();

        // Set OSM file
        hopper.setOSMFile(osmFilePath);

        // Set graph storage location
        hopper.setGraphHopperLocation(graphLocation);

        hopper.setEncodedValuesString("road_access");

        TurnCostsConfig turnCostsConfig = new TurnCostsConfig(
                List.of("motorcar", "motor_vehicle", "delivery"),
                60
        );


        CustomModel customModel = new CustomModel();

        customModel.addToSpeed(Statement.If(
                "true",
                Statement.Op.LIMIT,
                "50"
        ));

        customModel.addToSpeed(Statement.If(
                "road_access == DESTINATION",
                Statement.Op.MULTIPLY,
                "0.5"
        ));

        Profile carProfile = new Profile("car")
                .setWeighting("custom")
                .setCustomModel(customModel)
                .setTurnCostsConfig(turnCostsConfig);

        hopper.setProfiles(carProfile);

        // Enable Contraction Hierarchies for faster routing
        hopper.getCHPreparationHandler()
                .setCHProfiles(new CHProfile("car"));

        // Import or load (will use cached graph if available)
        long startTime = System.currentTimeMillis();
        hopper.importOrLoad();
        long elapsed = System.currentTimeMillis() - startTime;

        log.info("✓ Graph built/loaded in {} seconds", elapsed / 1000);

        return hopper;
    }

    @Override
    public DistanceMatrixEntry fetch(OptCoordinates from, OptCoordinates to) {

        GHRequest request = new GHRequest(
                from.latDouble(),
                from.lonDouble(),
                to.latDouble(),
                to.lonDouble()
        )
                .setProfile("car")
                .setLocale(Locale.ENGLISH);

        GHResponse response = graphHopper.route(request);

        if (response.hasErrors()) {
            log.warn("GraphHopper routing error: {}", response.getErrors());
            throw new IllegalStateException("Routing failed: " + response.getErrors());
        }

        var path = response.getBest();

        return new DistanceMatrixEntry(
                Duration.ofMillis(path.getTime()),
                Distance.ofMeters(path.getDistance())
        );
    }

    @PreDestroy
    public void cleanup() {
        if (graphHopper != null) {
            log.info("Closing GraphHopper...");
            graphHopper.close();
        }
    }

}
