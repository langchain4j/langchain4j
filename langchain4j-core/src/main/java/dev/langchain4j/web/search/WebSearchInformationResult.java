package dev.langchain4j.web.search;

import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents general information about the web search performed.
 * This includes the total number of results, the page number, and metadata.
 * <p>
 * The total number of results is the total number of web pages that are found by the search engine in response to a search query.
 * The page number is the current page number of the search results.
 * The metadata is a map of key-value pairs that provide additional information about the search.
 * For example, it could include the search query, the search engine used, the time it took to perform the search, etc.
 */
public class WebSearchInformationResult {

    private final Long totalResults;
    private final Integer pageNumber;
    private final Map<String, Object> metadata;

    /**
     * Constructs a new WebSearchInformationResult with the specified total results.
     *
     * @param totalResults The total number of results.
     */
    public WebSearchInformationResult(Long totalResults) {
        this(totalResults, null, null);
    }

    /**
     * Constructs a new WebSearchInformationResult with the specified total results, page number, and metadata.
     *
     * @param totalResults The total number of results.
     * @param pageNumber  The page number.
     * @param metadata     The metadata.
     */
    public WebSearchInformationResult(Long totalResults, Integer pageNumber, Map<String, Object> metadata) {
        this.totalResults = ensureNotNull(totalResults, "totalResults");
        this.pageNumber = pageNumber;
        this.metadata = copy(metadata);
    }

    /**
     * Gets the total number of results.
     *
     * @return The total number of results.
     */
    public Long totalResults() {
        return totalResults;
    }

    /**
     * Gets the page number.
     *
     * @return The page number.
     */
    public Integer pageNumber() {
        return pageNumber;
    }

    /**
     * Gets the metadata.
     *
     * @return The metadata.
     */
    public Map<String, Object> metadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WebSearchInformationResult that = (WebSearchInformationResult) o;
        return Objects.equals(totalResults, that.totalResults)
                && Objects.equals(pageNumber, that.pageNumber)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalResults, pageNumber, metadata);
    }

    @Override
    public String toString() {
        return "WebSearchInformationResult{" +
                "totalResults=" + totalResults +
                ", pageNumber=" + pageNumber +
                ", metadata=" + metadata +
                '}';
    }

    /**
     * Creates a new WebSearchInformationResult with the specified total results.
     *
     * @param totalResults The total number of results.
     * @return The new WebSearchInformationResult.
     */
    public static WebSearchInformationResult from(Long totalResults) {
        return new WebSearchInformationResult(totalResults);
    }

    /**
     * Creates a new WebSearchInformationResult with the specified total results, page number, and metadata.
     *
     * @param totalResults The total number of results.
     * @param pageNumber  The page number.
     * @param metadata     The metadata.
     * @return The new WebSearchInformationResult.
     */
    public static WebSearchInformationResult from(Long totalResults, Integer pageNumber, Map<String, Object> metadata) {
        return new WebSearchInformationResult(totalResults, pageNumber, metadata);
    }
}
