package org.tasktelemetry.matchsimulator.engine.team;

import java.util.Objects;

public record Player(String name, Role role, PlayerAttributes attributes) {

    public Player {
        Objects.requireNonNull(name, "player name must not be null");
        Objects.requireNonNull(role, "player role must not be null");
        Objects.requireNonNull(attributes, "player attributes must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("player name must not be blank");
        }
    }
}
