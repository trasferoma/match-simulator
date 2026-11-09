package org.tasktelemetry.matchsimulator.teamfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

final class TeamFileFixture {

    private static final String PROPERTY = "teams.dir";
    private static final String HOME_EXAMPLE = "esempio-casa.json";
    private static final String AWAY_EXAMPLE = "esempio-ospite.json";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private TeamFileFixture() {
    }

    static Path homeExample() {
        return examplesDirectory().resolve(HOME_EXAMPLE);
    }

    static Path awayExample() {
        return examplesDirectory().resolve(AWAY_EXAMPLE);
    }

    static ObjectNode validTeam() {
        try {
            return (ObjectNode) MAPPER.readTree(Files.readString(homeExample()));
        } catch (IOException exception) {
            throw new IllegalStateException("example team file cannot be read", exception);
        }
    }

    static Path write(Path directory, JsonNode content) {
        return writeText(directory, MAPPER.writeValueAsString(content));
    }

    static Path writeText(Path directory, String content) {
        try {
            Path file = directory.resolve("team.json");
            Files.writeString(file, content);
            return file;
        } catch (IOException exception) {
            throw new IllegalStateException("test team file cannot be written", exception);
        }
    }

    private static Path examplesDirectory() {
        String value = System.getProperty(PROPERTY);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("system property " + PROPERTY + " is not set (Surefire of match-app"
                    + " sets it)");
        }
        return Path.of(value);
    }
}
