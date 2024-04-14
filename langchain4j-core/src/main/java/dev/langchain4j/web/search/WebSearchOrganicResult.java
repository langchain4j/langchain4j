package dev.langchain4j.web.search;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents an organic search results are the web pages that are returned by the search engine in response to a search query.
 * This includes the title, link, snippet, content, and metadata of the web page.
 * <p>
 * These results are typically ranked by relevance to the search query.
 * <p>
 */
public class WebSearchOrganicResult {
    private final String title;
    private final URI url;
    private final String snippet;
    private final Map<String, String> metadata;


    /**
     * Constructs a WebSearchOrganicResult object with the given URL and content.
     *
     * @param title The title of the search result.
     * @param url The URL associated with the search result.
     */
    public WebSearchOrganicResult(String title, URI url) {
        this.title = ensureNotBlank(title, "title");
        this.url = ensureNotNull(url, "url");
        this.snippet = null;
        this.metadata = null;
    }

    /**
     * Constructs a WebSearchOrganicResult object with the given URL, title, and content.
     *
     * @param title   The title of the search result.
     * @param url    The URL associated with the search result.
     * @param snippet The snippet of the search result, in plain text.
     */
    public WebSearchOrganicResult(String title, URI url, String snippet) {
        this.title = ensureNotBlank(title, "title");
        this.url = ensureNotNull(url, "url");
        this.snippet = ensureNotBlank(snippet, "snippet");
        this.metadata = null;
    }

    /**
     * Constructs a WebSearchOrganicResult object with the given URL, title, content, and metadata.
     *
     *
     * @param title           The title of the search result.
     * @param url             The URL associated with the search result.
     * @param snippet         The snippet of the search result, in plain text.
     * @param metadata  The metadata associated with the search result.
     */
    public WebSearchOrganicResult(String title, URI url, String snippet, Map<String, String> metadata) {
        this.title = ensureNotBlank(title, "title");
        this.url = ensureNotNull(url,"url");
        this.snippet = ensureNotBlank(snippet, "snippet");
        this.metadata = getOrDefault(metadata, new HashMap<>());
    }

    /**
     * Returns the title of the search result.
     *
     * @return The title of the search result.
     */
    public String title() {
        return title;
    }

    /**
     * Returns the URL associated with the search result.
     *
     * @return The URL associated with the search result.
     */
    public URI url() {
        return url;
    }

    /**
     * Returns the snippet associated of the search result.
     *
     * @return The snippet associated of the search result.
     */
    public String snippet() {
        return snippet;
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
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, url, snippet, metadata);
    }

    @Override
    public String toString() {
        return "WebSearchOrganicResult{" +
                "title='" + title + '\'' +
                ", url=" + url +
                ", snippet='" + snippet + '\'' +
                ", metadata=" + metadata +
                '}';
    }

    /**
     * Converts this WebSearchOrganicResult to a TextSegment.
     *
     * @return The TextSegment representation of this WebSearchOrganicResult.
     */
    public TextSegment toTextSegment() {
        return TextSegment.from(snippet,
                copyToMetadata());
    }

    /**
     * Converts this WebSearchOrganicResult to a Document.
     *
     * @return The Document representation of this WebSearchOrganicResult.
     */
    public Document toDocument() {
        return Document.from(snippet,
                copyToMetadata());
    }

    private Metadata copyToMetadata() {
        Metadata docMetadata = new Metadata();
        docMetadata.add("title", title);
        docMetadata.add("url", url);
        if (metadata != null) {
            Map<String, String> tempMap= docMetadata.asMap();
            tempMap.putAll(metadata);
            docMetadata = Metadata.from(tempMap);
        }
        return docMetadata;
    }

    /**
     * Creates a WebSearchOrganicResult object from the given content and link.
     *
     * @param title   The title of the search result.
     * @param url    The URL associated with the search result.
     * @return The created WebSearchOrganicResult object.
     */
    public static WebSearchOrganicResult from(String title, URI url) {
        return new WebSearchOrganicResult(title, url);
    }

    /**
     * Creates a WebSearchOrganicResult object from the given title, link, and content.
     *
     * @param title   The title of the search result.
     * @param url    The URL associated with the search result.
     * @param snippet The snippet of the search result, in plain text.
     * @return The created WebSearchOrganicResult object.
     */
    public static WebSearchOrganicResult from(String title, URI url, String snippet) {
        return new WebSearchOrganicResult(title, url, snippet);
    }

    /**
     * Creates a WebSearchOrganicResult object from the given title, link, content, and result metadata.
     *
     * @param title           The title of the search result.
     * @param url            The URL associated with the search result.
     * @param snippet         The snippet of the search result, in plain text.
     * @param metadata  The metadata associated with the search result.
     * @return The created WebSearchOrganicResult object.
     */
    public static WebSearchOrganicResult from(String title, URI url, String snippet, Map<String, String> metadata) {
        return new WebSearchOrganicResult(title, url, snippet, metadata);
    }
}
