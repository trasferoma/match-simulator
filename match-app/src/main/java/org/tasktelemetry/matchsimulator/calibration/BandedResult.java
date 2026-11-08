package org.tasktelemetry.matchsimulator.calibration;

import java.util.Locale;
import java.util.Objects;

public record BandedResult(String label, double value, Band band, BandOrigin origin) {

    public enum BandOrigin {
        READ("letta"),
        COMPUTED("calcolata");

        private final String description;

        BandOrigin(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }

    public BandedResult {
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(band, "band must not be null");
        Objects.requireNonNull(origin, "band origin must not be null");
    }

    public boolean isInside() {
        return band.contains(value);
    }

    public String render() {
        String verdict = isInside() ? "DENTRO" : "FUORI";
        return String.format(Locale.ITALY, "%-30s %9.2f   forbice %s %7.2f - %7.2f   %s", label, value,
                origin.description(), band.low(), band.high(), verdict);
    }
}
