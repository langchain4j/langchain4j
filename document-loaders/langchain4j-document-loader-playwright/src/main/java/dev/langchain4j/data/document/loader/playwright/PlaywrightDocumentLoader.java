package dev.langchain4j.data.document.loader.playwright;

import static java.util.Objects.requireNonNull;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
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
     * Loads a document from the specified URL.
     *
     * @param url            The URL of the file. Must not be null.
     * @return document
     */
    public Document load(String url) {
        requireNonNull(url, "url must not be null");
        logger.info("Loading document from URL: {}", url);
        String pageContent;
        try {
            final Page page = browser.newPage();
            page.navigate(url);

            // Most of the time, this method is not needed because Playwright auto-waits before every action.
            // https://playwright.dev/java/docs/actionability
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            logger.debug("Waiting webpage fully loaded: {}", url);
            pageContent = page.content();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load document from URL: " + url, e);
        }
        return Document.from(pageContent, Metadata.from(Document.URL, url));
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
