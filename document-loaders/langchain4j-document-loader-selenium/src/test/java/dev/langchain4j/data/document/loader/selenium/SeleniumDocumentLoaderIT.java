package dev.langchain4j.data.document.loader.selenium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.transformer.jsoup.HtmlToTextDocumentTransformer;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.testcontainers.containers.BrowserWebDriverContainer;

class SeleniumDocumentLoaderIT {

    static SeleniumDocumentLoader loader;
    static BrowserWebDriverContainer<?> chromeContainer;
    static WebDriver webDriver;

    DocumentParser parser = new TextDocumentParser();
    HtmlToTextDocumentTransformer extractor = new HtmlToTextDocumentTransformer();

    @BeforeAll
    static void beforeAll() {
        chromeContainer = new BrowserWebDriverContainer<>().withCapabilities(new ChromeOptions());
        chromeContainer.start();
        webDriver = new RemoteWebDriver(chromeContainer.getSeleniumAddress(), new ChromeOptions());
        loader = SeleniumDocumentLoader.builder()
                .webDriver(webDriver)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @AfterAll
    static void afterAll() {
        if (loader != null) {
            loader.close();
        }
        if (webDriver != null) {
            webDriver.quit();
        }
        if (chromeContainer != null) {
            chromeContainer.stop();
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
        SeleniumDocumentLoader customLoader = SeleniumDocumentLoader.builder()
                .webDriver(webDriver)
                .timeout(Duration.ofSeconds(10))
                .build()
                .pageReadyCondition((ExpectedCondition<Boolean>) wd -> true); // Always ready
        String url =
                "https://raw.githubusercontent.com/langchain4j/langchain4j/main/langchain4j/src/test/resources/test-file-utf8.txt";
        Document document = customLoader.load(url, parser);
        assertThat(document.text()).contains("test");
        customLoader.close();
    }
}
