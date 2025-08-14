package dev.langchain4j.web.search;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.toList;

/**
 * Represents the response of a web search performed.
 * This includes the list of organic search results, information about the search, and pagination information.
 * <p>
 * {@link WebSearchResults} follow opensearch foundation standard implemented by most web search engine libs like Google, Bing, Yahoo, etc.
 * <a href="https://github.com/dewitt/opensearch/blob/master/opensearch-1-1-draft-6.md#examples-of-opensearch-responses">OpenSearch#response</a>
 * </p>
 * <p>
 * The organic search results are the web pages that are returned by the search engine in response to a search query.
 * These results are typically ranked by relevance to the search query.
 */
public class WebSearchResults {

    private final Map<String, Object> searchMetadata;
    private final WebSearchInformationResult searchInformation;
    private final List<WebSearchOrganicResult> results;

    /**
     * Constructs a new instance of WebSearchResults.
     *
     * @param searchInformation The information about the web search.
     * @param results           The list of organic search results.
     */
    public WebSearchResults(WebSearchInformationResult searchInformation, List<WebSearchOrganicResult> results) {
        this(Map.of(), searchInformation, results);
    }

    /**
     * Constructs a new instance of WebSearchResults.
     *
     * @param searchMetadata    The metadata associated with the web search.
     * @param searchInformation The information about the web search.
     * @param results           The list of organic search results.
     */
    public WebSearchResults(Map<String, Object> searchMetadata,
                            WebSearchInformationResult searchInformation,
                            List<WebSearchOrganicResult> results) {
        this.searchMetadata = copy(searchMetadata);
        this.searchInformation = ensureNotNull(searchInformation, "searchInformation");
        this.results = copy(results);
    }

    /**
     * Gets the metadata associated with the web search.
     *
     * @return The metadata associated with the web search.
     */
    public Map<String, Object> searchMetadata() {
        return searchMetadata;
    }

    /**
     * Gets the information about the web search.
     *
     * @return The information about the web search.
     */
    public WebSearchInformationResult searchInformation() {
        return searchInformation;
    }

    /**
     * Gets the list of organic search results.
     *
     * @return The list of organic search results.
     */
    public List<WebSearchOrganicResult> results() {
        return results;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WebSearchResults that = (WebSearchResults) o;
        return Objects.equals(searchMetadata, that.searchMetadata)
                && Objects.equals(searchInformation, that.searchInformation)
                && Objects.equals(results, that.results);
    }

    @Override
    public int hashCode() {
        return Objects.hash(searchMetadata, searchInformation, results);
    }

    @Override
    public String toString() {
        return "WebSearchResults{" +
                "searchMetadata=" + searchMetadata +
                ", searchInformation=" + searchInformation +
                ", results=" + results +
                '}';
    }

    /**
     * Converts the organic search results to a list of text segments.
     *
     * @return The list of text segments.
     */
    public List<TextSegment> toTextSegments() {
        return results.stream()
                .map(WebSearchOrganicResult::toTextSegment)
                .collect(toList());
    }

    /**
     * Converts the organic search results to a list of documents.
     *
     * @return The list of documents.
     */
    public List<Document> toDocuments() {
        return results.stream()
                .map(WebSearchOrganicResult::toDocument)
                .collect(toList());
    }

    /**
     * Creates a new instance of WebSearchResults from the specified parameters.
     *
     * @param results           The list of organic search results.
     * @param searchInformation The information about the web search.
     * @return The new instance of WebSearchResults.
     */
    public static WebSearchResults from(WebSearchInformationResult searchInformation, List<WebSearchOrganicResult> results) {
        return new WebSearchResults(searchInformation, results);
    }

    /**
     * Creates a new instance of WebSearchResults from the specified parameters.
     *
     * @param searchMetadata    The metadata associated with the search results.
     * @param searchInformation The information about the web search.
     * @param results           The list of organic search results.
     * @return The new instance of WebSearchResults.
     */
    public static WebSearchResults from(Map<String, Object> searchMetadata, WebSearchInformationResult searchInformation, List<WebSearchOrganicResult> results) {
        return new WebSearchResults(searchMetadata, searchInformation, results);
    }
}
