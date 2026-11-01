package com.curio.news.service;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleTextFetcherTest {

    private static final String PARA =
            "Anthropic released a new model today that reads longer documents and answers faster than before. ";

    @Test
    void extractText_keepsTheArticleBody_andDropsPageChrome() throws Exception {
        String html = "<html><head><script>var x=1;</script></head><body>"
                + "<nav><p>" + PARA + "NAV</p></nav>"
                + "<article><h2>What changed</h2><p>" + PARA + "</p><p>Share this</p><p>" + PARA + PARA + "</p></article>"
                + "<footer><p>" + PARA + "FOOTER</p></footer></body></html>";

        String text = ArticleTextFetcher.extractText(html.getBytes(StandardCharsets.UTF_8), "https://example.com/a");

        assertThat(text).startsWith("What changed\n").contains("Anthropic released a new model")
                .doesNotContain("NAV").doesNotContain("FOOTER").doesNotContain("Share this").doesNotContain("var x");
    }

    @Test
    void extractText_capsTheLength_andTreatsNearEmptyPagesAsNoText() throws Exception {
        String longHtml = "<main>" + ("<p>" + PARA + "</p>").repeat(60) + "</main>";
        String shellHtml = "<body><div id=app></div><p>Loading the latest news for you, please wait a moment.</p></body>";

        assertThat(ArticleTextFetcher.extractText(longHtml.getBytes(StandardCharsets.UTF_8), "https://e.com"))
                .hasSizeLessThanOrEqualTo(ArticleTextFetcher.MAX_TEXT_CHARS);
        assertThat(ArticleTextFetcher.extractText(shellHtml.getBytes(StandardCharsets.UTF_8), "https://e.com"))
                .isEmpty();
    }

    @Test
    void refusesUrlsThatPointInsideOurNetwork() throws Exception {
        assertThat(ArticleTextFetcher.isPublicHttpUrl(URI.create("http://127.0.0.1/admin"))).isFalse();
        assertThat(ArticleTextFetcher.isPublicHttpUrl(URI.create("http://localhost:8080/"))).isFalse();
        assertThat(ArticleTextFetcher.isPublicHttpUrl(URI.create("http://169.254.169.254/latest/meta-data"))).isFalse();
        assertThat(ArticleTextFetcher.isPublicHttpUrl(URI.create("http://10.0.0.5/"))).isFalse();
        assertThat(ArticleTextFetcher.isPublicHttpUrl(URI.create("http://[::1]/"))).isFalse();
        assertThat(ArticleTextFetcher.isPublicHttpUrl(URI.create("file:///etc/passwd"))).isFalse();
        assertThat(ArticleTextFetcher.isPublicHttpUrl(URI.create("javascript:alert(1)"))).isFalse();

        assertThat(ArticleTextFetcher.isPublicAddress(InetAddress.getByName("100.64.0.1"))).isFalse();
        assertThat(ArticleTextFetcher.isPublicAddress(InetAddress.getByName("192.168.1.1"))).isFalse();
        assertThat(ArticleTextFetcher.isPublicAddress(InetAddress.getByName("fd00::1"))).isFalse();
        assertThat(ArticleTextFetcher.isPublicAddress(InetAddress.getByName("93.184.216.34"))).isTrue();
    }
}
