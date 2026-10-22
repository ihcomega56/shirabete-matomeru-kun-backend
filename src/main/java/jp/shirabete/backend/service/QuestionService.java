package jp.shirabete.backend.service;

import jp.shirabete.backend.api.QuestionResponse;
import jp.shirabete.backend.domain.Report;
import jp.shirabete.backend.domain.ReportSummary;
import jp.shirabete.backend.research.ResearchProvider;
import jp.shirabete.backend.research.ResearchResult;
import jp.shirabete.backend.repository.ReportSearch;
import jp.shirabete.backend.repository.ReportStore;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class QuestionService {
    private final ResearchProvider researchProvider;
    private final ReportStore store;
    private final ReportSearch search;
    private final ReportRenderer renderer;
    private final Clock clock;

    public QuestionService(ResearchProvider researchProvider, ReportStore store, ReportSearch search,
                           ReportRenderer renderer, Clock clock) {
        this.researchProvider = researchProvider;
        this.store = store;
        this.search = search;
        this.renderer = renderer;
        this.clock = clock;
    }

    public QuestionResponse ask(String question) {
        String normalized = question.strip();
        ResearchResult result = researchProvider.research(normalized);
        String title = ReportRenderer.truncate(normalized.replaceAll("(?U)\\s+", " "), 80);
        StringBuilder markdown = new StringBuilder("# ").append(ReportRenderer.escapeText(title))
                .append("\n\n## 質問\n\n").append(ReportRenderer.escapeText(normalized))
                .append("\n\n## 回答\n\n").append(ReportRenderer.escapeText(result.answer()))
                .append("\n\n## 出典\n\n");
        for (int i = 0; i < result.sources().size(); i++) {
            var source = result.sources().get(i);
            markdown.append(i + 1).append(". [").append(ReportRenderer.escapeText(source.title()))
                    .append("](<").append(source.url().replace("<", "%3C").replace(">", "%3E"))
                    .append(">)\n");
        }
        if (result.sources().isEmpty()) {
            markdown.append("出典は提供されていません。\n");
        }
        String content = markdown.toString();
        Report report = new Report(UUID.randomUUID(), title, content, renderer.toHtml(content), clock.instant());
        store.save(report);
        return new QuestionResponse(result.answer(), result.sources(), report);
    }

    public List<ReportSummary> search(String query) {
        return search.search(query);
    }

    public Report get(UUID id) {
        return store.findById(id).orElseThrow(ReportNotFoundException::new);
    }
}
