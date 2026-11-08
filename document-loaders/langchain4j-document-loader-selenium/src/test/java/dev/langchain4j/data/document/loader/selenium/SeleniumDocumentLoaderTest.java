package dev.langchain4j.data.document.loader.selenium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

class SeleniumDocumentLoaderTest {

    private static final String NON_ASCII_CONTENT = "café ☕ 日本語 €";
    private static final String URL = "https://example.com";

    @Test
    void load_should_encode_page_content_as_utf8_before_parsing() {
        WebDriver webDriver = mock(WebDriver.class);
        when(webDriver.getPageSource()).thenReturn(NON_ASCII_CONTENT);

        SeleniumDocumentLoader loader = SeleniumDocumentLoader.builder()
                .webDriver(webDriver)
                .timeout(Duration.ofSeconds(1))
                .build()
                .pageReadyCondition(wd -> true); // skip the JavascriptExecutor readyState wait

        // Capture the exact bytes the parser receives from the loader.
        AtomicReference<byte[]> capturedBytes = new AtomicReference<>();
        DocumentParser capturingParser = inputStream -> {
            capturedBytes.set(readAllBytes(inputStream));
            return Document.from("parsed");
        };

        Document document = loader.load(URL, capturingParser);

        // The handed-off bytes must be the UTF-8 encoding of the page content, not the platform default.
        assertThat(capturedBytes.get()).isEqualTo(NON_ASCII_CONTENT.getBytes(StandardCharsets.UTF_8));
        assertThat(document.metadata().getString(Document.URL)).isEqualTo(URL);
    }

    private static byte[] readAllBytes(InputStream inputStream) {
        try {
            return inputStream.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
