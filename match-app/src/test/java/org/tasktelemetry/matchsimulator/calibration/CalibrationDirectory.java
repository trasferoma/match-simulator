package org.tasktelemetry.matchsimulator.calibration;

import java.nio.file.Files;
import java.nio.file.Path;

final class CalibrationDirectory {

    private static final String PROPERTY = "calibration.dir";

    private CalibrationDirectory() {
    }

    static Path fromSystemProperty() {
        String value = System.getProperty(PROPERTY);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("system property " + PROPERTY + " is not set: the calibration data"
                    + " directory must be passed explicitly (Surefire of match-app sets it)");
        }
        Path directory = Path.of(value);
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("system property " + PROPERTY + " points to " + directory
                    + ", which is not a directory");
        }
        return directory;
    }
}
