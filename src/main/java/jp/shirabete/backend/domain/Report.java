package jp.shirabete.backend.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Report(UUID id, String title, String markdown, String html, Instant createdAt) {
    public Report {
        Objects.requireNonNull(id);
        Objects.requireNonNull(title);
        Objects.requireNonNull(markdown);
        Objects.requireNonNull(html);
        Objects.requireNonNull(createdAt);
    }
}
