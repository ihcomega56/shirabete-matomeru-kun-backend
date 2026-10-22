package jp.shirabete.backend.repository;

import jp.shirabete.backend.config.AppProperties;
import jp.shirabete.backend.domain.Report;
import jp.shirabete.backend.domain.ReportSummary;
import jp.shirabete.backend.service.ReportRenderer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "local")
public class LocalFileReportRepository implements ReportStore, ReportSearch {
    private final Path directory;
    private final ObjectMapper mapper;
    private final ReportRenderer renderer;

    public LocalFileReportRepository(AppProperties properties, ObjectMapper mapper, ReportRenderer renderer) {
        this.directory = properties.storage().directory().toAbsolutePath().normalize();
        this.mapper = mapper;
        this.renderer = renderer;
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new StorageException("Cannot initialize report directory", e);
        }
    }

    @Override
    public synchronized void save(Report report) {
        Path staging = directory.resolve("." + report.id() + "-" + UUID.randomUUID() + ".staging");
        try {
            byte[] bytes = mapper.writeValueAsBytes(report);
            try (FileChannel channel = FileChannel.open(staging, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            // Same-directory atomic rename prevents partially written JSON from becoming visible.
            Files.move(staging, path(report.id()), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            throw new StorageException("Cannot save report", e);
        } finally {
            try {
                Files.deleteIfExists(staging);
            } catch (IOException ignored) {
                // An abandoned staging file is never included in search.
            }
        }
    }

    @Override
    public synchronized Optional<Report> findById(UUID id) {
        Path file = path(id);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        return Optional.of(read(file));
    }

    @Override
    public synchronized List<ReportSummary> search(String query) {
        List<String> terms = Arrays.stream(query.strip().toLowerCase(Locale.ROOT).split("(?U)\\s+"))
                .filter(term -> !term.isEmpty()).toList();
        try (var files = Files.list(directory)) {
            return files.filter(file -> file.getFileName().toString().matches(
                            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.json"))
                    .map(this::read)
                    .filter(report -> {
                        String text = (report.title() + "\n" + report.markdown()).toLowerCase(Locale.ROOT);
                        return terms.stream().allMatch(text::contains);
                    })
                    .sorted(Comparator.comparing(Report::createdAt).reversed().thenComparing(Report::id))
                    .map(report -> new ReportSummary(report.id(), report.title(),
                            renderer.summary(report.markdown()), report.createdAt()))
                    .toList();
        } catch (IOException | RuntimeException e) {
            throw new StorageException("Cannot search reports", e);
        }
    }

    private Report read(Path file) {
        try {
            Report report = mapper.readValue(Files.readAllBytes(file), Report.class);
            if (!file.getFileName().equals(path(report.id()).getFileName())) {
                throw new IllegalArgumentException("Report ID does not match file name");
            }
            // Re-render on read so stored HTML is never trusted, including after renderer upgrades.
            return new Report(report.id(), report.title(), report.markdown(),
                    renderer.toHtml(report.markdown()), report.createdAt());
        } catch (IOException | RuntimeException e) {
            throw new StorageException("Cannot read report", e);
        }
    }

    private Path path(UUID id) {
        return directory.resolve(id + ".json");
    }
}
