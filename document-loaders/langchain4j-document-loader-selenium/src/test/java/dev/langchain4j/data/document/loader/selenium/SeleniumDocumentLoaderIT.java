package dev.langchain4j.data.document.loader.selenium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.transformer.HtmlTextExtractor;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testcontainers.containers.BrowserWebDriverContainer;

class SeleniumDocumentLoaderIT {

    static SeleniumDocumentLoader loader;

    DocumentParser parser = new TextDocumentParser();
    HtmlTextExtractor extractor = new HtmlTextExtractor();

    @BeforeAll
    static void beforeAll() {
        BrowserWebDriverContainer<?> chromeContainer = new BrowserWebDriverContainer<>()
            .withCapabilities(new ChromeOptions());
        chromeContainer.start();
        RemoteWebDriver webDriver = new RemoteWebDriver(chromeContainer.getSeleniumAddress(), new ChromeOptions());
        loader = SeleniumDocumentLoader.builder()
                .webDriver(webDriver)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @Test
    void should_load_html_document() {
        String url =
            "https://raw.githubusercontent.com/langchain4j/langchain4j/main/langchain4j/src/test/resources/test-file-utf8.txt";
        Document document = loader.load(url, parser);

        Document textDocument = extractor.transform(document);

        assertThat(textDocument.text()).isEqualTo("test content");
        assertThat(document.text()).contains("test\ncontent");
        assertThat(document.metadata(Document.URL)).isEqualTo(url);
    }

    @Test
    void should_fail_for_unresolvable_url() {
        String url =
            "https://a.a";
        assertThatExceptionOfType(RuntimeException.class)
            .isThrownBy(() -> loader.load(url, parser));
    }

    @Test
    void should_fail_for_bad_url() {
        String url =
            "bad_url";
        assertThatExceptionOfType(RuntimeException.class)
            .isThrownBy(() -> loader.load(url, parser));
    }
}