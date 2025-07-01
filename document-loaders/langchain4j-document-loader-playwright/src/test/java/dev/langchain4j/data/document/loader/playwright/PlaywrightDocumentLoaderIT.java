package dev.langchain4j.data.document.loader.playwright;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.microsoft.playwright.Browser;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.transformer.jsoup.HtmlToTextDocumentTransformer;
import io.orangebuffalo.testcontainers.playwright.PlaywrightContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlaywrightDocumentLoaderIT {

    static PlaywrightDocumentLoader loader;
    static PlaywrightContainer playwrightContainer;
    static Browser browser;

    DocumentParser parser = new TextDocumentParser();
    HtmlToTextDocumentTransformer extractor = new HtmlToTextDocumentTransformer();

    @BeforeAll
    static void beforeAll() {
        playwrightContainer = new PlaywrightContainer();
        playwrightContainer.start();
        browser = playwrightContainer.getPlaywrightApi().chromium();
        loader = PlaywrightDocumentLoader.builder().browser(browser).build();
    }

    @AfterAll
    static void afterAll() {
        if (loader != null) {
            loader.close();
        }
        if (browser != null) {
            browser.close();
        }
        if (playwrightContainer != null) {
            playwrightContainer.stop();
        }
    }

    @BeforeEach
    void beforeEach() {
        // Reset or re-initialize state if needed
    }

    @Test
    void should_load_html_document() {
        String url =
                "https://raw.githubusercontent.com/langchain4j/langchain4j/main/langchain4j/src/test/resources/test-file-utf8.txt";
        Document document = loader.load(url, parser);
        Document textDocument = extractor.transform(document);
        assertThat(textDocument.text()).isEqualTo("test content");
        assertThat(document.text()).contains("test\ncontent");
        assertThat(document.metadata().getString(Document.URL)).isEqualTo(url);
    }

    @Test
    void should_fail_for_unresolvable_url() {
        String url = "https://a.a";
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> loader.load(url, parser))
                .withMessageContaining("Failed to load document from URL");
    }

    @Test
    void should_fail_for_bad_url() {
        String url = "bad_url";
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> loader.load(url, parser))
                .withMessageContaining("Failed to load document from URL");
    }

    @Test
    void should_support_custom_page_ready_condition() {
        PlaywrightDocumentLoader customLoader =
                PlaywrightDocumentLoader.builder().browser(browser).build();
        String url =
                "https://raw.githubusercontent.com/langchain4j/langchain4j/main/langchain4j/src/test/resources/test-file-utf8.txt";
        Document document = customLoader.load(url, parser);
        assertThat(document.text()).contains("test");
        customLoader.close();
    }
}
