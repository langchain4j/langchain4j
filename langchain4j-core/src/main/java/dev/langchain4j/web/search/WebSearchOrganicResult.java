package dev.langchain4j.web.search;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents an organic search results are the web pages that are returned by the search engine in response to a search query.
 * This includes the title, URL, snippet and/or content, and metadata of the web page.
 * <p>
 * These results are typically ranked by relevance to the search query.
 * <p>
 */
public class WebSearchOrganicResult {

    private final String title;
    private final URI url;
    private final String snippet;
    private final String content;
    private final Map<String, String> metadata;

    /**
     * Constructs a WebSearchOrganicResult object with the given title and URL.
     *
     * @param title The title of the search result.
     * @param url The URL associated with the search result.
     */
    public WebSearchOrganicResult(String title, URI url) {
        this.title = ensureNotBlank(title, "title");
        this.url = ensureNotNull(url, "url");
        this.snippet = null;
        this.content = null;
        this.metadata = Map.of();
    }

    /**
     * Constructs a WebSearchOrganicResult object with the given title, URL, snippet and/or content.
     *
     * @param title   The title of the search result.
     * @param url    The URL associated with the search result.
     * @param snippet The snippet of the search result, in plain text.
     * @param content The most query related content from the scraped url.
     */
    public WebSearchOrganicResult(String title, URI url, String snippet, String content) {
        this.title = ensureNotBlank(title, "title");
        this.url = ensureNotNull(url, "url");
        this.snippet = snippet;
        this.content = content;
        this.metadata = Map.of();
    }

    /**
     * Constructs a WebSearchOrganicResult object with the given title, URL, snippet and/or content, and metadata.
     *
     * @param title           The title of the search result.
     * @param url             The URL associated with the search result.
     * @param snippet         The snippet of the search result, in plain text.
     * @param content The most query related content from the scraped url.
     * @param metadata  The metadata associated with the search result.
     */
    public WebSearchOrganicResult(String title, URI url, String snippet, String content, Map<String, String> metadata) {
        this.title = ensureNotBlank(title, "title");
        this.url = ensureNotNull(url,"url");
        this.snippet = snippet;
        this.content = content;
        this.metadata = copy(metadata);
    }

    /**
     * Returns the title of the web page.
     *
     * @return The title of the web page.
     */
    public String title() {
        return title;
    }

    /**
     * Returns the URL associated with the web page.
     *
     * @return The URL associated with the web page.
     */
    public URI url() {
        return url;
    }

    /**
     * Returns the snippet associated with the web page.
     *
     * @return The snippet associated with the web page.
     */
    public String snippet() {
        return snippet;
    }

    /**
     * Returns the content scraped from the web page.
     *
     * @return The content scraped from the web page.
     */
    public String content() {
        return content;
    }

    /**
     * Returns the result metadata associated with the search result.
     *
     * @return The result metadata associated with the search result.
     */
    public Map<String, String> metadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WebSearchOrganicResult that = (WebSearchOrganicResult) o;
        return Objects.equals(title, that.title)
                && Objects.equals(url, that.url)
                && Objects.equals(snippet, that.snippet)
                && Objects.equals(content, that.content)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, url, snippet, content, metadata);
    }

    @Override
    public String toString() {
        return "WebSearchOrganicResult{" +
                "title='" + title + '\'' +
                ", url=" + url +
                ", snippet='" + snippet + '\'' +
                ", content='" + content + '\'' +
                ", metadata=" + metadata +
                '}';
    }

    /**
     * Converts this WebSearchOrganicResult to a TextSegment.
     *
     * @return The TextSegment representation of this WebSearchOrganicResult.
     */
    public TextSegment toTextSegment() {
        return TextSegment.from(copyToText(), copyToMetadata());
    }

    /**
     * Converts this WebSearchOrganicResult to a Document.
     *
     * @return The Document representation of this WebSearchOrganicResult.
     */
    public Document toDocument() {
        return Document.from(copyToText(), copyToMetadata());
    }

    private String copyToText() {
        StringBuilder text = new StringBuilder();
        text.append(title);
        text.append("\n");
        if (isNotNullOrBlank(content)) {
            text.append(content);
        } else if (isNotNullOrBlank(snippet)) {
            text.append(snippet);
        }
        return text.toString();
    }

    private Metadata copyToMetadata() {
        Metadata docMetadata = new Metadata();
        docMetadata.put("url", url.toString());
        if (metadata != null) {
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                docMetadata.put(entry.getKey(), entry.getValue());
            }
        }
        return docMetadata;
    }

    /**
     * Creates a WebSearchOrganicResult object from the given title and URL.
     *
     * @param title   The title of the search result.
     * @param url    The URL associated with the search result.
     * @return The created WebSearchOrganicResult object.
     */
    public static WebSearchOrganicResult from(String title, URI url) {
        return new WebSearchOrganicResult(title, url);
    }

    /**
     * Creates a WebSearchOrganicResult object from the given title, URL, snippet and/or content.
     *
     * @param title   The title of the search result.
     * @param url    The URL associated with the search result.
     * @param snippet The snippet of the search result, in plain text.
     * @param content The most query related content from the scraped url.
     * @return The created WebSearchOrganicResult object.
     */
    public static WebSearchOrganicResult from(String title, URI url, String snippet, String content) {
        return new WebSearchOrganicResult(title, url, snippet, content);
    }

    /**
     * Creates a WebSearchOrganicResult object from the given title, URL, snippet and/or content, and result metadata.
     *
     * @param title           The title of the search result.
     * @param url            The URL associated with the search result.
     * @param snippet         The snippet of the search result, in plain text.
     * @param content The most query related content from the scraped url.
     * @param metadata  The metadata associated with the search result.
     * @return The created WebSearchOrganicResult object.
     */
    public static WebSearchOrganicResult from(String title, URI url, String snippet, String content, Map<String, String> metadata) {
        return new WebSearchOrganicResult(title, url, snippet, content, metadata);
    }
}
