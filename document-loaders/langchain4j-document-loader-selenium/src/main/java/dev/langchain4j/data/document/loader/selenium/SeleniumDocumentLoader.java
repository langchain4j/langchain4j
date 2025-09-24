package dev.langchain4j.data.document.loader.selenium;

import static java.util.Objects.requireNonNull;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
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
 * Returns a {@link Document} object containing the content of the web page.
 *
 */
public class SeleniumDocumentLoader {

    private static final Logger logger = LoggerFactory.getLogger(SeleniumDocumentLoader.class);
    private static final Duration DEFAULT_TIMEOUT_DURATION = Duration.ofSeconds(30);

    private final WebDriver webDriver;
    private final Duration timeout;

    private SeleniumDocumentLoader(WebDriver webDriver, Duration timeout) {
        this.webDriver = webDriver;
        this.timeout = timeout;
    }

    /**
     * Loads a document from the specified URL.
     *
     * @param url            The URL of the file.
     * @param documentParser The parser to be used for parsing text from the URL.
     * @return document
     */
    public Document load(String url, DocumentParser documentParser) {
        logger.info("Loading document from URL: {}", url);
        String pageContent;
        try {
            webDriver.get(url);
            WebDriverWait wait = new WebDriverWait(webDriver, timeout);
            logger.debug("Waiting webpage fully loaded: {}", url);
            wait.until((ExpectedCondition<Boolean>) wd -> {
                if (logger.isTraceEnabled()) {
                    logger.trace("Waiting for document.readyState to be complete");
                }
                return ((JavascriptExecutor) requireNonNull(wd)).executeScript("return document.readyState").equals("complete");
            });
            pageContent = webDriver.getPageSource();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load document", e);
        }
        Document parsedDocument = documentParser.parse(new ByteArrayInputStream(pageContent.getBytes()));
        parsedDocument.metadata().put(Document.URL, url);
        return parsedDocument;
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
