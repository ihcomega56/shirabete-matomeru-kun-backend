package jp.shirabete.backend.repository;

import jp.shirabete.backend.domain.Report;

import java.util.Optional;
import java.util.UUID;

public interface ReportStore {
    /** Returns only after the storage write has completed successfully. */
    void save(Report report);
    Optional<Report> findById(UUID id);
}
