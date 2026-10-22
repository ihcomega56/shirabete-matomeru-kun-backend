package jp.shirabete.backend.api;

import jp.shirabete.backend.domain.Report;
import jp.shirabete.backend.domain.Source;

import java.util.List;

public record QuestionResponse(String answer, List<Source> sources, Report report) {}
