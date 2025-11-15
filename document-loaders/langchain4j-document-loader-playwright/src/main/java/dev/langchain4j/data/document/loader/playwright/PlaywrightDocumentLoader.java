package dev.langchain4j.data.document.loader.playwright;

import static java.util.Objects.requireNonNull;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import java.io.ByteArrayInputStream;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for loading web documents using Playwright.
 * Returns a {@link Document} object containing the content of the Web page.
 */
public class PlaywrightDocumentLoader implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(PlaywrightDocumentLoader.class);

    private final Browser browser;

    private PlaywrightDocumentLoader(Browser browser) {
        this.browser = requireNonNull(browser, "browser must not be null");
    }

    /**
     * Loads a {@link Document} from the specified URL by fetching its HTML content.
     * <p>
     * This method fetches the page content using {@code pageContent(url)}, and then wraps
     * it into a {@link Document} with metadata indicating the source URL.
     * </p>
     *
     * @param url the URL of the web page to load; must not be {@code null}
     * @return a {@link Document} instance containing the HTML content and metadata
     * @throws NullPointerException if the provided {@code url} is {@code null}
     * @throws RuntimeException if the document fails to load due to an underlying content fetch issue
     */
    public Document load(String url) {
        String pageContent = pageContent(url);
        return Document.from(pageContent, Metadata.from(Document.URL, url));
    }

    /**
     * Loads a {@link Document} from the specified URL by fetching its HTML content
     * and parsing it using the provided {@link DocumentParser}.
     * <p>
     * This method delegates content parsing to the given parser and adds the source URL
     * to the document's metadata.
     * </p>
     *
     * @param url the URL of the web page to load; must not be {@code null}
     * @param documentParser the parser used to convert raw HTML content into a {@link Document}; must not be {@code null}
     * @return a {@link Document} parsed from the web page content, with metadata including the URL
     * @throws NullPointerException if {@code url} or {@code documentParser} is {@code null}
     * @throws RuntimeException if the document content cannot be loaded or parsed
     */
    public Document load(String url, DocumentParser documentParser) {
        requireNonNull(documentParser, "documentParser must not be null");

        String pageContent = pageContent(url);

        Document parsedDocument = documentParser.parse(new ByteArrayInputStream(pageContent.getBytes()));

        parsedDocument.metadata().put(Document.URL, url);
        return parsedDocument;
    }

    /**
     * Loads the HTML content of a web page from the specified URL using Playwright.
     * <p>
     * This method opens a new page in the browser, navigates to the given URL,
     * waits for the DOM content to be fully loaded, and returns the full HTML content of the page.
     * </p>
     *
     * @param url the URL of the web page to load; must not be {@code null}
     * @return the full HTML content of the page as a {@code String}
     * @throws NullPointerException if the provided {@code url} is {@code null}
     * @throws RuntimeException if the page fails to load or an unexpected error occurs during navigation
     */
    public String pageContent(String url) {
        requireNonNull(url, "url must not be null");
        logger.info("Loading document from URL: {}", url);

        try (Page page = browser.newPage()) {
            logger.info("Navigating to URL: {}", url);
            page.navigate(url);

            // Explicit wait to ensure DOM is loaded (optional in most Playwright actions)
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            logger.debug("Web page fully loaded: {}", url);
            return page.content();

        } catch (Exception e) {
            throw new RuntimeException("Failed to load document from URL: " + url, e);
        }
    }
    /**
     * Closes the underlying Browser instance.
     */
    @Override
    public void close() {
        if (browser != null) {
            try {
                browser.close();
                logger.info("Browser closed successfully.");
            } catch (Exception e) {
                logger.warn("Error closing Browser", e);
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Browser browser;

        public Builder browser(Browser browser) {
            this.browser = browser;
            return this;
        }

        public PlaywrightDocumentLoader build() {
            Objects.requireNonNull(browser, "browser must be set");
            return new PlaywrightDocumentLoader(browser);
        }
    }
}
