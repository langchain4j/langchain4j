package dev.langchain4j.web.search;

/**
 * Represents a web search engine that can be used to perform searches on the Web in response to a user query.
 */
public interface WebSearchEngine {

    /**
     * Performs a search query on the web search engine and returns the search results.
     *
     * @param query the search query
     * @return the search results
     */
    default WebSearchResults search(String query) {
        return search(WebSearchRequest.from(query));
    }

    /**
     * Performs a search request on the web search engine and returns the search results.
     *
     * @param webSearchRequest the search request
     * @return the web search results
     */
    WebSearchResults search(WebSearchRequest webSearchRequest);
}
