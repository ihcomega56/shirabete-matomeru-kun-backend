package jp.shirabete.backend.repository;

import jp.shirabete.backend.domain.ReportSummary;

import java.util.List;

public interface ReportSearch {
    /** Blank queries list all reports; implementations return newest first. */
    List<ReportSummary> search(String query);
}
