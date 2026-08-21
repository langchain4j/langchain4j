package dev.langchain4j.web.search;

import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.internal.AsyncNotSupported;
import java.util.concurrent.CompletableFuture;

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

    /**
     * Non-blocking counterpart of {@link #search(WebSearchRequest)}, used by the asynchronous and reactive RAG flow
     * (see {@code WebSearchContentRetriever}).
     * <p>
     * The default returns a failed future carrying {@link AsyncNotSupportedException}: a web search engine that is not genuinely asynchronous
     * does not pretend to be. An engine backed by remote HTTP I/O opts in by overriding this with a genuinely async
     * call (no thread parked). An engine that has not opted in is still usable from the non-blocking RAG path, which
     * offloads its blocking {@link #search(WebSearchRequest)}.
     * <p>
     * An implementation that honors cancellation should abort its in-flight call when the returned future is
     * cancelled (best-effort).
     *
     * @param webSearchRequest the search request
     * @return a {@link CompletableFuture} of the web search results
     * @since 1.19.0
     */
    default CompletableFuture<WebSearchResults> searchAsync(WebSearchRequest webSearchRequest) {
        return AsyncNotSupported.failedFuture(getClass(), "searchAsync");
    }
}
