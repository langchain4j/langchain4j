package dev.langchain4j.rag.content.retriever;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.toList;

/**
 * A {@link ContentRetriever} that retrieves relevant {@link Content} from the web using a {@link WebSearchEngine}.
 * <br>
 * It returns one {@link Content} for each result that a {@link WebSearchEngine} has returned for a given {@link Query}.
 * <br>
 * Depending on the {@link WebSearchEngine} implementation, the {@link Content#textSegment()}
 * can contain either a snippet of a web page or a complete content of a web page.
 */
public class WebSearchContentRetriever implements ContentRetriever {

    private final WebSearchEngine webSearchEngine;
    private final int maxResults;

    public WebSearchContentRetriever(WebSearchEngine webSearchEngine, Integer maxResults) {
        this.webSearchEngine = ensureNotNull(webSearchEngine, "webSearchEngine");
        this.maxResults = getOrDefault(maxResults, 5);
    }

    public static WebSearchContentRetrieverBuilder builder() {
        return new WebSearchContentRetrieverBuilder();
    }

    @Override
    public List<Content> retrieve(Query query) {

        WebSearchResults webSearchResults = webSearchEngine.search(toWebSearchRequest(query));

        return toContents(webSearchResults);
    }

    @Override
    public CompletableFuture<List<Content>> retrieveAsync(Query query) {
        // Deliver a synchronously-throwing searchAsync (e.g. the throwing default of a blocking engine) through the
        // returned future rather than throwing, honoring the async error contract (consistent with the augmentor's
        // nativeOrOffload, which detects an AsyncNotSupportedException from this future).
        CompletableFuture<WebSearchResults> searchFuture;
        try {
            searchFuture = webSearchEngine.searchAsync(toWebSearchRequest(query));
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }
        CompletableFuture<List<Content>> result = searchFuture.thenApply(WebSearchContentRetriever::toContents);
        // Link the caller-facing derived stage back to the raw search call so cancellation reaches the in-flight I/O.
        propagateCancellation(result, searchFuture);
        return result;
    }

    private WebSearchRequest toWebSearchRequest(Query query) {
        return WebSearchRequest.builder()
                .searchTerms(query.text())
                .maxResults(maxResults)
                .build();
    }

    private static List<Content> toContents(WebSearchResults webSearchResults) {
        return webSearchResults.toTextSegments().stream()
                .map(Content::from)
                .collect(toList());
    }

    public static class WebSearchContentRetrieverBuilder {
        private WebSearchEngine webSearchEngine;
        private Integer maxResults;

        WebSearchContentRetrieverBuilder() {
        }

        public WebSearchContentRetrieverBuilder webSearchEngine(WebSearchEngine webSearchEngine) {
            this.webSearchEngine = webSearchEngine;
            return this;
        }

        public WebSearchContentRetrieverBuilder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public WebSearchContentRetriever build() {
            return new WebSearchContentRetriever(this.webSearchEngine, this.maxResults);
        }
    }
}
