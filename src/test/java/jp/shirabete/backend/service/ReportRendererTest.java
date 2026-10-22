package jp.shirabete.backend.service;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportRendererTest {
    private final ReportRenderer renderer = new ReportRenderer();

    @Test
    void convertsMarkdownAndPreservesSafeSourceLinks() {
        String html = renderer.toHtml("# 見出し\n\n**要点**\n\n[出典](https://learn.microsoft.com/ja-jp/azure/)\n\n- 項目");
        assertThat(html).contains("<h1>見出し</h1>", "<strong>要点</strong>", "<ul>",
                "href=\"https://learn.microsoft.com/ja-jp/azure/\"", "noopener noreferrer");
    }

    @Test
    void disablesRawHtmlAndUnsafeSchemesAndImages() {
        String html = renderer.toHtml("""
                <script>alert(1)</script>
                
                <img src=x onerror=alert(1)>
                
                [bad](javascript:alert%281%29)
                
                [data](data:text/html,test)
                
                ![tracking](https://evil.example/pixel)
                
                <iframe src="https://evil.example"></iframe>
                """);
        var document = Jsoup.parse(html);
        assertThat(document.select("script, img, iframe, [onerror]")).isEmpty();
        assertThat(document.select("a[href]")).isEmpty();
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    void plainTextEscapingAndSummaryHandleUnicode() {
        String value = "# <b>質問</b> [click](javascript:evil) **text**";
        assertThat(Jsoup.parse(renderer.toHtml(ReportRenderer.escapeText(value))).text()).isEqualTo(value);
        assertThat(renderer.summary("# タイトル\n\n**説明** [資料](https://example.com)"))
                .isEqualTo("タイトル 説明 資料");
        assertThat(renderer.summary("😀".repeat(250)).codePointCount(0,
                renderer.summary("😀".repeat(250)).length())).isEqualTo(201);
    }
}
