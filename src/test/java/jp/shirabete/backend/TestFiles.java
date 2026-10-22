package jp.shirabete.backend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

public final class TestFiles {
    private TestFiles() {}

    public static Path directory(String name) {
        try {
            return Files.createDirectories(Path.of("target", "test-data", name + "-" + UUID.randomUUID()));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void delete(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }
}
