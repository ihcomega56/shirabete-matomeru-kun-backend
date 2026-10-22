package jp.shirabete.backend.domain;

import java.net.URI;
import java.util.Objects;

public record Source(String title, String url) {
    public Source {
        Objects.requireNonNull(title);
        Objects.requireNonNull(url);
        URI uri = URI.create(url);
        if (title.isBlank() || uri.getHost() == null
                || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("Sources require a title and an absolute HTTP(S) URL");
        }
    }
}
