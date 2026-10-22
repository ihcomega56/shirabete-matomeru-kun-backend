package jp.shirabete.backend.service;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class ReportRenderer {
    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder()
            .escapeHtml(true).sanitizeUrls(true).build();
    private final Safelist safelist = Safelist.basic()
            .addTags("h1", "h2", "h3", "h4", "h5", "h6", "hr")
            .removeProtocols("a", "href", "ftp", "mailto")
            .addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer");

    public String toHtml(String markdown) {
        return Jsoup.clean(renderer.render(parser.parse(markdown)), safelist);
    }

    public String summary(String markdown) {
        String text = Jsoup.parse(toHtml(markdown)).text();
        return truncate(text, 200);
    }

    public static String truncate(String text, int maxCodePoints) {
        int count = text.codePointCount(0, text.length());
        return count <= maxCodePoints ? text : text.substring(0, text.offsetByCodePoints(0, maxCodePoints)) + "…";
    }

    /** Treat questions and provider answers as text, not as executable Markdown. */
    public static String escapeText(String text) {
        StringBuilder escaped = new StringBuilder();
        text.codePoints().forEach(codePoint -> {
            if (codePoint < 128 && ((codePoint >= 33 && codePoint <= 47)
                    || (codePoint >= 58 && codePoint <= 64) || (codePoint >= 91 && codePoint <= 96)
                    || (codePoint >= 123 && codePoint <= 126))) {
                escaped.append('\\');
            }
            escaped.appendCodePoint(codePoint);
        });
        return escaped.toString();
    }
}
