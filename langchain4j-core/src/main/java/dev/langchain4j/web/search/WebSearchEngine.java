package dev.langchain4j.web.search;

import java.util.stream.Collectors;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents a web search engine that can be used to perform searches on the Web in response to a user query.
 */
public interface WebSearchEngine {

    /**
     * Runs a search query on the web search engine and returns a pretty-string representation of the search results.
     *
     * @param query the search query
     * @return a pretty-string representation of the search results
     */
    default String runSearch(String query) {
        return search(query).results()
                .stream()
                .map(organicResult -> organicResult.title() + "\n" + organicResult.content() + "\n" + "Source: " + organicResult.link())
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * Performs a search query on the web search engine and returns the search results.
     *
     * @param query the search query
     * @return the search results
     * @throws IllegalArgumentException if no web search results are found for the query
     */
    default WebSearchResults search(String query) {
        WebSearchResults results = search(WebSearchRequest.from(query));
        ensureNotNull(results, "No web search results found for query: " + query);
        return results;
    }

    /**
     * Performs a search request on the web search engine and returns the search results.
     *
     * @param webSearchRequest the search request
     * @return the web search results
     */
    WebSearchResults search(WebSearchRequest webSearchRequest);
}
