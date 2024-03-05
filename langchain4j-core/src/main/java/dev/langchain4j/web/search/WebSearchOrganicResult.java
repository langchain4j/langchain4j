package dev.langchain4j.web.search;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents an organic search results are the web pages that are returned by the search engine in response to a search query.
 * This includes the title, link, content, and metadata of the web page.
 * <p>
 * These results are typically ranked by relevance to the search query.
 * <p>
 */
public class WebSearchOrganicResult {

    private final String title;
    private final String link;
    private final String content;
    private final Map<String, String> resultMetadata;

    /**
     * Constructs a WebSearchOrganicResult object with the given content.
     *
     * @param content The content of the search result.
     */
    public WebSearchOrganicResult(String content) {
        this("noTitle", "noLink", content, null);
    }

    /**
     * Constructs a WebSearchOrganicResult object with the given content and link.
     *
     * @param content The content of the search result.
     * @param link    The link associated with the search result.
     */
    public WebSearchOrganicResult(String content, String link) {
        this("noTitle", link, content, null);
    }

    /**
     * Constructs a WebSearchOrganicResult object with the given title, link, and content.
     *
     * @param title   The title of the search result.
     * @param link    The link associated with the search result.
     * @param content The content of the search result.
     */
    public WebSearchOrganicResult(String title, String link, String content) {
        this(title, link, content, null);
    }

    /**
     * Constructs a WebSearchOrganicResult object with the given title, link, content, and result metadata.
     *
     * @param title           The title of the search result.
     * @param link            The link associated with the search result.
     * @param content         The content of the search result.
     * @param resultMetadata  The metadata associated with the search result.
     */
    public WebSearchOrganicResult(String title, String link, String content, Map<String, String> resultMetadata) {
        this.title = ensureNotNull(title, "title");
        this.link = ensureNotNull(link, "link");
        this.content = ensureNotBlank(content, "content");
        this.resultMetadata = getOrDefault(resultMetadata, new HashMap<>());
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
     * Returns the link associated with the search result.
     *
     * @return The link associated with the search result.
     */
    public String link() {
        return link;
    }

    /**
     * Returns the content of the search result.
     *
     * @return The content of the search result.
     */
    public String content() {
        return content;
    }

    /**
     * Returns the result metadata associated with the search result.
     *
     * @return The result metadata associated with the search result.
     */
    public Map<String, String> resultMetadata() {
        return resultMetadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WebSearchOrganicResult that = (WebSearchOrganicResult) o;
        return Objects.equals(title, that.title)
                && Objects.equals(link, that.link)
                && Objects.equals(content, that.content)
                && Objects.equals(resultMetadata, that.resultMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, link, content, resultMetadata);
    }

    @Override
    public String toString() {
        return "WebSearchOrganicResult{" +
                "title='" + title + '\'' +
                ", link='" + link + '\'' +
                ", content='" + content + '\'' +
                ", resultMetadata=" + resultMetadata +
                '}';
    }

    /**
     * Converts this WebSearchOrganicResult to a TextSegment.
     *
     * @return The TextSegment representation of this WebSearchOrganicResult.
     */
    public TextSegment toTextSegment() {
        return TextSegment.from(content,
                Metadata.from(resultMetadata)
                        .add("title", title)
                        .add("link", link));
    }

    /**
     * Converts this WebSearchOrganicResult to a Document.
     *
     * @return The Document representation of this WebSearchOrganicResult.
     */
    public Document toDocument() {
        return Document.from(content,
                Metadata.from(resultMetadata)
                        .add("title", title)
                        .add("link", link));
    }

    /**
     * Creates a WebSearchOrganicResult object from the given content.
     *
     * @param content The content of the search result.
     * @return The created WebSearchOrganicResult object.
     */
    public static WebSearchOrganicResult from(String content) {
        return new WebSearchOrganicResult(content);
    }

    /**
     * Creates a WebSearchOrganicResult object from the given content and link.
     *
     * @param content The content of the search result.
     * @param link    The link associated with the search result.
     * @return The created WebSearchOrganicResult object.
     */
    public static WebSearchOrganicResult from(String content, String link) {
        return new WebSearchOrganicResult(content, link);
    }

    /**
     * Creates a WebSearchOrganicResult object from the given title, link, and content.
     *
     * @param title   The title of the search result.
     * @param link    The link associated with the search result.
     * @param content The content of the search result.
     * @return The created WebSearchOrganicResult object.
     */
    public static WebSearchOrganicResult from(String title, String link, String content) {
        return new WebSearchOrganicResult(title, link, content);
    }

    /**
     * Creates a WebSearchOrganicResult object from the given title, link, content, and result metadata.
     *
     * @param title           The title of the search result.
     * @param link            The link associated with the search result.
     * @param content         The content of the search result.
     * @param resultMetadata  The metadata associated with the search result.
     * @return The created WebSearchOrganicResult object.
     */
    public static WebSearchOrganicResult from(String title, String link, String content, Map<String, String> resultMetadata) {
        return new WebSearchOrganicResult(title, link, content, resultMetadata);
    }

    /**
     * Creates a WebSearchOrganicResult object from the given content.
     *
     * @param content The content of the search result.
     * @return The created WebSearchOrganicResult object.
     */
    public static WebSearchOrganicResult webSearchOrganicResult(String content) {
        return from(content);
    }

    /**
     * Creates a WebSearchOrganicResult object from the given content and link.
     *
     * @param content The content of the search result.
     * @param link    The link associated with the search result.
     * @return The created WebSearchOrganicResult object.
     */
    public static WebSearchOrganicResult webSearchOrganicResult(String content, String link) {
        return from(content, link);
    }

    /**
     * Creates a WebSearchOrganicResult object from the given title, link, and content.
     *
     * @param title   The title of the search result.
     * @param link    The link associated with the search result.
     * @param content The content of the search result.
     * @return The created WebSearchOrganicResult object.
     */
    public static WebSearchOrganicResult webSearchOrganicResult(String title, String link, String content) {
        return from(title, link, content);
    }

    /**
     * Creates a WebSearchOrganicResult object from the given title, link, content, and result metadata.
     *
     * @param title           The title of the search result.
     * @param link            The link associated with the search result.
     * @param content         The content of the search result.
     * @param resultMetadata  The metadata associated with the search result.
     * @return The created WebSearchOrganicResult object.
     */
    public static WebSearchOrganicResult webSearchOrganicResult(String title, String link, String content, Map<String, String> resultMetadata) {
        return from(title, link, content, resultMetadata);
    }
}
