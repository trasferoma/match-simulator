package org.tasktelemetry.matchsimulator.engine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

final class EngineSources {

    private final Path moduleDirectory = locateModuleDirectory();

    List<SourceFile> mainSources() {
        Path root = moduleDirectory.resolve("src").resolve("main").resolve("java");
        try (Stream<Path> files = Files.walk(root)) {
            return files
                    .filter(file -> file.toString().endsWith(".java"))
                    .sorted()
                    .map(file -> read(root, file))
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("cannot list the sources of the engine under " + root, exception);
        }
    }

    String pomText() {
        Path pom = moduleDirectory.resolve("pom.xml");
        try {
            return Files.readString(pom, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("cannot read the pom of the engine at " + pom, exception);
        }
    }

    private static SourceFile read(Path root, Path file) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            String relativePath = root.relativize(file).toString();
            return SourceFile.fromText(relativePath, text);
        } catch (IOException exception) {
            throw new UncheckedIOException("cannot read the source " + file, exception);
        }
    }

    private static Path locateModuleDirectory() {
        Path base = Path.of(System.getProperty("basedir", System.getProperty("user.dir"))).toAbsolutePath();
        Path insideModule = base.resolve("src").resolve("main").resolve("java");
        if (Files.isDirectory(insideModule)) {
            return base;
        }
        return base.resolve("match-engine");
    }
}
