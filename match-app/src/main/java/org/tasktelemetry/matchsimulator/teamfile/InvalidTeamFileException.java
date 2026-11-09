package org.tasktelemetry.matchsimulator.teamfile;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class InvalidTeamFileException extends RuntimeException {

    private final transient Path file;
    private final String field;

    private InvalidTeamFileException(Path file, String field, String message, Throwable cause) {
        super(message, cause);
        this.file = file;
        this.field = field;
    }

    static InvalidTeamFileException forFile(Path file, String message, Throwable cause) {
        Objects.requireNonNull(file, "team file must not be null");
        return new InvalidTeamFileException(file, null, message, cause);
    }

    static InvalidTeamFileException forField(Path file, String field, String message) {
        Objects.requireNonNull(file, "team file must not be null");
        Objects.requireNonNull(field, "field must not be null");
        return new InvalidTeamFileException(file, field, message, null);
    }

    public Path file() {
        return file;
    }

    public Optional<String> field() {
        return Optional.ofNullable(field);
    }
}
