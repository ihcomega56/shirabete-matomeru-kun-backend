package jp.shirabete.backend.repository;

import jp.shirabete.backend.TestFiles;
import jp.shirabete.backend.config.AppProperties;
import jp.shirabete.backend.domain.Report;
import jp.shirabete.backend.service.ReportRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class LocalFileReportRepositoryTest {
    private final Path directory = TestFiles.directory("repository");

    @AfterEach
    void cleanup() throws Exception {
        TestFiles.delete(directory);
    }

    private LocalFileReportRepository repository() {
        var properties = new AppProperties(new AppProperties.Research(AppProperties.ResearchMode.LOCAL),
                new AppProperties.Storage(AppProperties.StorageMode.LOCAL, directory),
                new AppProperties.Search(AppProperties.SearchMode.LOCAL),
                new AppProperties.Cors(List.of("http://localhost:5173")));
        return new LocalFileReportRepository(properties, JsonMapper.builder().build(), new ReportRenderer());
    }

    private Report report(String title, String body, Instant time) {
        return new Report(UUID.randomUUID(), title, body, new ReportRenderer().toHtml(body), time);
    }

    @Test
    void survivesRepositoryRecreationAndSearchesTitleAndBodyNewestFirst() {
        var repository = repository();
        Report first = report("Azure 入門", "# 認証\n\nマネージドID の使用方法", Instant.parse("2026-01-01T00:00:00Z"));
        Report second = report("新しい報告", "AZURE のマネージドID", Instant.parse("2026-02-01T00:00:00Z"));
        repository.save(first);
        repository.save(second);
        var reopened = repository();
        assertThat(reopened.findById(first.id())).contains(first);
        assertThat(reopened.search(" azure マネージドID ")).extracting(summary -> summary.id())
                .containsExactly(second.id(), first.id());
        assertThat(reopened.search("認証")).extracting(summary -> summary.id()).containsExactly(first.id());
        assertThat(reopened.search("")).hasSize(2);
        assertThat(reopened.search("not-present")).isEmpty();
        assertThat(reopened.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void neverTrustsPersistedHtmlAndIgnoresUnfinishedWrites() throws Exception {
        var repository = repository();
        Report report = new Report(UUID.randomUUID(), "title", "# safe", "<script>bad</script>", Instant.now());
        repository.save(report);
        Files.writeString(directory.resolve(".abandoned.staging"), "{broken");
        Files.writeString(directory.resolve("unrelated.json"), "{broken");
        assertThat(repository.findById(report.id()).orElseThrow().html()).isEqualTo("<h1>safe</h1>");
        assertThat(repository.search("")).hasSize(1);
    }

    @Test
    void corruptionAndWriteFailuresAreExplicit() throws Exception {
        UUID id = UUID.randomUUID();
        Files.writeString(directory.resolve(id + ".json"), "{broken");
        assertThatThrownBy(() -> repository().findById(id)).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> repository().search("")).isInstanceOf(StorageException.class);
        TestFiles.delete(directory);
        Files.writeString(directory, "not a directory");
        assertThatThrownBy(this::repository).isInstanceOf(StorageException.class);
    }

    @Test
    void concurrentRequestsDoNotLoseReportsOrLeavePartialFiles() throws Exception {
        var repository = repository();
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (int i = 0; i < 12; i++) {
                executor.submit(() -> repository.save(report("並行", "report body", Instant.now())));
            }
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(repository().search("")).hasSize(12);
        try (var files = Files.list(directory)) {
            assertThat(files.toList()).allMatch(file -> file.toString().endsWith(".json"));
        }
    }
}
