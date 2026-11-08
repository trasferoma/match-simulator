package org.tasktelemetry.matchsimulator.calibration;

public record Band(double low, double high) {

    public Band {
        if (low > high) {
            throw new IllegalArgumentException("band low " + low + " must not exceed high " + high);
        }
    }

    public boolean contains(double value) {
        return value >= low && value <= high;
    }
}
