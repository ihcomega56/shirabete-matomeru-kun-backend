package jp.shirabete.backend.domain;

import java.time.Instant;
import java.util.UUID;

public record ReportSummary(UUID id, String title, String summary, Instant createdAt) {}
