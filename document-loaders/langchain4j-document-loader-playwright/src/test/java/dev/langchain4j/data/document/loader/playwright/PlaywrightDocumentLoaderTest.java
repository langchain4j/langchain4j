package dev.langchain4j.data.document.loader.playwright;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlaywrightDocumentLoaderTest {

    // Pins the UTF-8 encoding contract for load(url, parser): the page content must be handed to the
    // DocumentParser as UTF-8 bytes, independent of the JVM's default charset. This is byte-identical
    // to String.getBytes() on a UTF-8-default JVM, so on such CI it is a contract-pin, not a fail-on-bug guard.
    @Test
    void load_should_encode_page_content_as_utf8_for_the_parser() throws Exception {
        String html = "<html><body>café © 日本語</body></html>";

        Page page = mock(Page.class);
        when(page.content()).thenReturn(html);

        Browser browser = mock(Browser.class);
        when(browser.newPage()).thenReturn(page);

        DocumentParser parser = mock(DocumentParser.class);
        when(parser.parse(any(InputStream.class))).thenReturn(Document.from("parsed"));

        PlaywrightDocumentLoader loader =
                PlaywrightDocumentLoader.builder().browser(browser).build();

        loader.load("https://example.com", parser);

        ArgumentCaptor<InputStream> captor = ArgumentCaptor.forClass(InputStream.class);
        verify(parser).parse(captor.capture());

        byte[] actualBytes = captor.getValue().readAllBytes();
        assertThat(actualBytes).isEqualTo(html.getBytes(StandardCharsets.UTF_8));
    }
}
