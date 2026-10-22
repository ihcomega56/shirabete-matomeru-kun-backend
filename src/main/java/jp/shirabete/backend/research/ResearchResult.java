package jp.shirabete.backend.research;

import jp.shirabete.backend.domain.Source;

import java.util.List;
import java.util.Objects;

/** Answer text is plain text; sources must be verified by the provider, not invented by the model. */
public record ResearchResult(String answer, List<Source> sources) {
    public ResearchResult {
        Objects.requireNonNull(answer);
        if (answer.isBlank()) {
            throw new IllegalArgumentException("Research answer must not be blank");
        }
        sources = List.copyOf(sources);
    }
}
