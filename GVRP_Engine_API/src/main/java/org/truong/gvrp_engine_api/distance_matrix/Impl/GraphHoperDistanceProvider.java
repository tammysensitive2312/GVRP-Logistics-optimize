package org.truong.gvrp_engine_api.distance_matrix.Impl;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.json.Statement;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.PointList;
import com.graphhopper.util.TurnCostsConfig;
import com.graphhopper.util.Translation;
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
import org.truong.gvrp_engine_api.distance_matrix.RoutePathProvider;
import org.truong.gvrp_engine_api.model.LegInstruction;
import org.truong.gvrp_engine_api.model.LegPath;
import org.truong.gvrp_engine_api.utils.PolylineEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.graphhopper.json.Statement.If;

@Slf4j
@Component
public class GraphHoperDistanceProvider implements DistanceProvider, RoutePathProvider {
    @Value("${graphhopper.osm.file}")
    private String osmFilePath;

    @Value("${graphhopper.graph.location}")
    private String graphLocation;

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
            // Same rule as path(): the GraphHopper message contains the point.
            log.warn("GraphHopper routing error ({} error(s))", response.getErrors().size());
            throw new IllegalStateException("Routing failed with " + response.getErrors().size() + " error(s)");
        }

        var path = response.getBest();

        return new DistanceMatrixEntry(
                Duration.ofMillis(path.getTime()),
                Distance.ofMeters(path.getDistance())
        );
    }

    /**
     * Routes one leg with the same router and profile the matrix uses, so a
     * drawn leg agrees with the costs the solver optimised (design §5.2).
     *
     * <p>GraphHopper computes turn instructions by default and falls back to
     * English when it has no translation for the requested locale.
     */
    @Override
    public LegPath path(OptCoordinates from, OptCoordinates to, Locale locale) {
        if (graphHopper == null) {
            throw new IllegalStateException("GraphHopper is not initialized");
        }

        GHRequest request = new GHRequest(
                from.latDouble(),
                from.lonDouble(),
                to.latDouble(),
                to.lonDouble()
        )
                .setProfile("car")
                .setLocale(locale != null ? locale : Locale.ENGLISH);

        GHResponse response = graphHopper.route(request);

        if (response.hasErrors()) {
            // The GraphHopper message names the offending point, so it is not logged
            // or rethrown: logs must not carry coordinates (AGENTS.md §15).
            log.warn("GraphHopper routing error ({} error(s))", response.getErrors().size());
            throw new IllegalStateException("Routing failed with " + response.getErrors().size() + " error(s)");
        }

        ResponsePath path = response.getBest();
        PointList points = path.getPoints();

        List<double[]> coordinates = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            coordinates.add(new double[]{points.getLat(i), points.getLon(i)});
        }

        InstructionList instructionList = path.getInstructions();
        Translation translation = instructionList != null ? instructionList.getTr() : null;

        List<LegInstruction> instructions = new ArrayList<>();
        if (instructionList != null) {
            for (Instruction instruction : instructionList) {
                String text = translation != null
                        ? instruction.getTurnDescription(translation)
                        : instruction.getName();
                instructions.add(new LegInstruction(
                        text,
                        instruction.getDistance(),
                        instruction.getTime(),
                        instruction.getSign(),
                        instruction.getName()
                ));
            }
        }

        return new LegPath(
                PolylineEncoder.encode(coordinates),
                path.getDistance(),
                path.getTime(),
                instructions
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
