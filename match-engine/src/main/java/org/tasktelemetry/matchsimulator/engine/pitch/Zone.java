package org.tasktelemetry.matchsimulator.engine.pitch;

public final class Zone {

    private final int index;

    Zone(int index) {
        this.index = index;
    }

    int index() {
        return index;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Zone zone && zone.index == index;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(index);
    }

    @Override
    public String toString() {
        return "Zone[" + index + "]";
    }
}
