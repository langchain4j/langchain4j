package dev.langchain4j.data.document.loader.selenium;

import static java.util.Objects.requireNonNull;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Objects;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for loading web documents using Selenium.
 * Returns a {@link Document} object containing the content of the Web page.
 */
public class SeleniumDocumentLoader implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(SeleniumDocumentLoader.class);
    private static final Duration DEFAULT_TIMEOUT_DURATION = Duration.ofSeconds(30);

    private final WebDriver webDriver;
    private final Duration timeout;
    private ExpectedCondition<Boolean> pageReadyCondition = wd -> {
        if (logger.isTraceEnabled()) {
            logger.trace("Waiting for document.readyState to be complete");
        }
        return ((JavascriptExecutor) requireNonNull(wd))
                .executeScript("return document.readyState")
                .equals("complete");
    };

    private SeleniumDocumentLoader(WebDriver webDriver, Duration timeout) {
        this.webDriver = requireNonNull(webDriver, "webDriver must not be null");
        this.timeout = requireNonNull(timeout, "timeout must not be null");
    }

    /**
     * Set a custom page ready condition for waiting until the page is loaded.
     * @param condition the ExpectedCondition to use
     * @return this loader instance
     */
    public SeleniumDocumentLoader pageReadyCondition(ExpectedCondition<Boolean> condition) {
        this.pageReadyCondition = requireNonNull(condition, "pageReadyCondition must not be null");
        return this;
    }

    /**
     * Loads a document from the specified URL and parses its content using the given {@link DocumentParser}.
     * <p>
     * This method uses the configured {@link WebDriver} to navigate to the provided URL, and retrieves the raw page content.
     * The content is then passed to the provided {@link DocumentParser} for parsing, and the resulting
     * {@link Document} is returned with the URL added to its metadata.
     * </p>
     *
     * @param url             The URL of the web page to load. Must not be null.
     * @param documentParser  The parser used to extract structured text from the loaded page content. Must not be null.
     * @return A {@link Document} containing parsed content and the source URL as metadata.
     * @throws NullPointerException if the {@code documentParser} is null.
     * @throws RuntimeException     if an error occurs while loading or retrieving the content from the URL.
     */
    public Document load(String url, DocumentParser documentParser) {
        requireNonNull(documentParser, "documentParser must not be null");
        logger.info("Loading document from URL: {}", url);
        String pageContent = pageContent(url);
        Document parsedDocument = documentParser.parse(new ByteArrayInputStream(pageContent.getBytes()));
        parsedDocument.metadata().put(Document.URL, url);
        return parsedDocument;
    }

    /**
     * Loads a document from the specified URL and wraps the raw page source as a {@link Document}.
     * <p>
     * This method fetches the content of the given URL using the configured {@link WebDriver},
     * waits until the page is fully loaded and returns a {@link Document} containing the raw HTML
     * or text content along with the source URL as metadata.
     * </p>
     *
     * @param url The URL to load the document from. Must not be null.
     * @return A {@link Document} containing the raw page source and the URL as metadata.
     * @throws RuntimeException if the page fails to load or an error occurs during retrieval.
     */
    public Document load(String url) {
        String pageContent = pageContent(url);
        return Document.from(pageContent, Metadata.from(Document.URL, url));
    }

    /**
     * Retrieves the full page source of the given URL using Selenium.
     * <p>
     * This method navigates the {@link WebDriver} to the specified URL, waits for the page
     * to be fully loaded, and then returns the page content as a string.
     * </p>
     *
     * @param url The URL to load. Must not be null.
     * @return The full HTML or text content of the loaded page.
     * @throws RuntimeException if an error occurs while loading the page or retrieving the content.
     */
    public String pageContent(String url) {
        requireNonNull(url, "url must not be null");

        try {
            logger.info("Navigating to URL: {}", url);
            webDriver.get(url);

            logger.debug("Waiting for page to load: {}", url);
            WebDriverWait wait = new WebDriverWait(webDriver, timeout);

            logger.debug("Waiting webpage fully loaded: {}", url);
            wait.until(pageReadyCondition);

            return webDriver.getPageSource();
        } catch (Exception e) {
            logger.error("Failed to load document from URL: {}", url, e);
            throw new RuntimeException("Failed to load document from URL: " + url, e);
        }
    }

    /**
     * Closes the underlying WebDriver instance.
     */
    @Override
    public void close() {
        logger.debug("Attempting to close WebDriver...");
        if (webDriver != null) {
            try {
                webDriver.quit();
                logger.info("WebDriver closed successfully.");
            } catch (Exception e) {
                logger.warn("Failed to close WebDriver. Resources may not be fully released.", e);
            }
        } else {
            logger.debug("WebDriver was already null. No action taken.");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private WebDriver webDriver;
        private Duration timeout = DEFAULT_TIMEOUT_DURATION;

        public Builder webDriver(WebDriver webDriver) {
            this.webDriver = webDriver;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public SeleniumDocumentLoader build() {
            Objects.requireNonNull(webDriver, "webDriver must be set");
            return new SeleniumDocumentLoader(webDriver, timeout);
        }
    }
}
