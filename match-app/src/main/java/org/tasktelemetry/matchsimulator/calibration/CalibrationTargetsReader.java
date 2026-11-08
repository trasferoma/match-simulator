package org.tasktelemetry.matchsimulator.calibration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class CalibrationTargetsReader {

    private static final String RESULTS_FILE = "big5-targets.json";
    private static final String MATCH_STATS_FILE = "big5-match-stats-targets.json";
    private static final String POSSESSION_FILE = "statsbomb-1516-targets.json";

    private final BenchConfig config;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public CalibrationTargetsReader(BenchConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    public CalibrationTargets read(Path directory) {
        Objects.requireNonNull(directory, "calibration directory must not be null");
        Map<String, Band> bandsByKey = readBandsByKey(directory);
        Map<BandedMetric, Band> bands = selectBands(bandsByKey, directory);
        Band possessionLow = computePossessionBand(directory, "p10");
        Band possessionHigh = computePossessionBand(directory, "p90");
        return new CalibrationTargets(bands, possessionLow, possessionHigh);
    }

    private Map<String, Band> readBandsByKey(Path directory) {
        Map<String, Band> bandsByKey = new LinkedHashMap<>();
        Path resultsFile = directory.resolve(RESULTS_FILE);
        Path matchStatsFile = directory.resolve(MATCH_STATS_FILE);
        JsonNode results = readDocument(resultsFile);
        JsonNode matchStats = readDocument(matchStatsFile);
        collectBands(results, resultsFile, bandsByKey);
        collectBands(matchStats, matchStatsFile, bandsByKey);
        return bandsByKey;
    }

    private void collectBands(JsonNode document, Path file, Map<String, Band> bandsByKey) {
        for (JsonNode metric : document.path("metrics")) {
            String key = metric.path("key").asString();
            double low = readNumber(metric.path("low"), "low of " + key, file);
            double high = readNumber(metric.path("high"), "high of " + key, file);
            bandsByKey.put(key, new Band(low, high));
        }
    }

    private double readNumber(JsonNode node, String field, Path file) {
        if (!node.isNumber()) {
            throw new IllegalArgumentException("field " + field + " must be a number in " + file);
        }
        return node.asDouble();
    }

    private Map<BandedMetric, Band> selectBands(Map<String, Band> bandsByKey, Path directory) {
        Map<BandedMetric, Band> bands = new EnumMap<>(BandedMetric.class);
        for (BandedMetric metric : BandedMetric.values()) {
            Band band = bandsByKey.get(metric.fileKey());
            if (band == null) {
                throw new IllegalArgumentException("metric " + metric.fileKey() + " not found in the files of "
                        + directory);
            }
            bands.put(metric, band);
        }
        return bands;
    }

    private Band computePossessionBand(Path directory, String percentileKey) {
        Path file = directory.resolve(POSSESSION_FILE);
        JsonNode document = readDocument(file);
        JsonNode shares = document.path("all_leagues").path("possession").path("time_share_per_team_match");
        double value = readNumber(shares.path(percentileKey), "possession percentile " + percentileKey, file);
        double tolerance = config.possessionTolerance();
        return new Band(value - tolerance, value + tolerance);
    }

    private JsonNode readDocument(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("calibration file not found: " + file);
        }
        try {
            String content = Files.readString(file);
            return mapper.readTree(content);
        } catch (JacksonException | IOException exception) {
            throw new IllegalArgumentException("calibration file cannot be read: " + file, exception);
        }
    }
}
