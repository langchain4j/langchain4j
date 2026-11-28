package dev.langchain4j.data.document.loader.playwright;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.transformer.jsoup.HtmlToTextDocumentTransformer;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

class PlaywrightDocumentLoaderIT {

    // the Playwright server in the container must have the same version as the Playwright client on the classpath
    static final String PLAYWRIGHT_VERSION = Playwright.class.getPackage().getImplementationVersion();
    static final int PLAYWRIGHT_SERVER_PORT = 3000;

    static PlaywrightDocumentLoader loader;
    static GenericContainer<?> playwrightContainer;
    static Playwright playwright;
    static Browser browser;

    HtmlToTextDocumentTransformer extractor = new HtmlToTextDocumentTransformer();
    private DocumentParser parser = new TextDocumentParser();

    @BeforeAll
    static void beforeAll() {
        playwrightContainer = new GenericContainer<>("mcr.microsoft.com/playwright:v" + PLAYWRIGHT_VERSION + "-noble")
                .withExposedPorts(PLAYWRIGHT_SERVER_PORT)
                .withCommand(
                        "npx",
                        "-y",
                        "playwright@" + PLAYWRIGHT_VERSION,
                        "run-server",
                        "--port",
                        String.valueOf(PLAYWRIGHT_SERVER_PORT),
                        "--host",
                        "0.0.0.0")
                .waitingFor(Wait.forLogMessage(".*Listening on.*", 1));
        playwrightContainer.start();

        // browsers run in the container, so there is no need to download them locally
        playwright = Playwright.create(
                new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
        browser = playwright
                .chromium()
                .connect("ws://" + playwrightContainer.getHost() + ":"
                        + playwrightContainer.getMappedPort(PLAYWRIGHT_SERVER_PORT) + "/");
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
        if (playwright != null) {
            playwright.close();
        }
        if (playwrightContainer != null) {
            playwrightContainer.stop();
        }
    }

    @Test
    void should_load_html_document() {
        String url = "https://docs.langchain4j.dev/apidocs/index.html";
        Document document = loader.load(url);
        Document textDocument = extractor.transform(document);
        assertThat(document.text()).contains("<head>");
        assertThat(textDocument.text()).doesNotContain("<head>");
        assertThat(textDocument.text()).contains("LangChain4j");
        assertThat(document.metadata().getString(Document.URL)).isEqualTo(url);
    }

    @Test
    void should_fail_for_unresolvable_url() {
        String url = "https://a.a";
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> loader.load(url))
                .withMessageContaining("Failed to load document from URL");
    }

    @Test
    void should_fail_for_bad_url() {
        String url = "bad_url";
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> loader.load(url))
                .withMessageContaining("Failed to load document from URL");
    }

    @Test
    void should_load_and_parse_html_document() {
        String url = "https://docs.langchain4j.dev/apidocs/index.html";

        Document document = loader.load(url, parser);

        assertThat(document.text()).contains("LangChain4j");
        assertThat(document.text()).contains("<head>");
        assertThat(document.metadata().getString(Document.URL)).isEqualTo(url);

        Document textDocument = extractor.transform(document);
        assertThat(textDocument.text()).doesNotContain("<head>");
        assertThat(textDocument.text()).contains("LangChain4j");
    }

    @Test
    void should_fail_for_unresolvable_url_with_parser() {
        String url = "https://a.a";

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> loader.load(url, parser))
                .withMessageContaining("Failed to load document from URL");
    }

    @Test
    void should_fail_for_bad_url_with_parser() {
        String url = "bad_url";

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> loader.load(url, parser))
                .withMessageContaining("Failed to load document from URL");
    }

    @Test
    void should_throw_null_pointer_exception_when_url_is_null() {
        assertThatNullPointerException()
                .isThrownBy(() -> loader.load(null, parser))
                .withMessage("url must not be null");
    }

    @Test
    void should_throw_null_pointer_exception_when_parser_is_null() {
        String url = "https://docs.langchain4j.dev/apidocs/index.html";

        assertThatNullPointerException()
                .isThrownBy(() -> loader.load(url, null))
                .withMessage("documentParser must not be null");
    }
}
