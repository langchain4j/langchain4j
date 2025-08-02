package dev.langchain4j.web.search.duckduckgo;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DuckDuckGoWebSearchEngine implements WebSearchEngine {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final DuckDuckGoClient duckDuckGoClient;

    public DuckDuckGoWebSearchEngine(Duration timeout, HttpClient httpClient) {
        this.duckDuckGoClient = new DuckDuckGoClient(httpClient, getOrDefault(timeout, DEFAULT_TIMEOUT));
    }

    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {
        List<DuckDuckGoSearchResult> results = duckDuckGoClient.search(
                webSearchRequest.searchTerms(), getOrDefault(webSearchRequest.maxResults(), 10));

        final List<WebSearchOrganicResult> organicResults = results.stream()
                .map(DuckDuckGoWebSearchEngine::toWebSearchOrganicResult)
                .collect(toList());

        return WebSearchResults.from(WebSearchInformationResult.from((long) organicResults.size()), organicResults);
    }

    public CompletableFuture<WebSearchResults> searchAsync(WebSearchRequest webSearchRequest) {
        return duckDuckGoClient
                .searchAsync(webSearchRequest.searchTerms(), getOrDefault(webSearchRequest.maxResults(), 10))
                .thenApply(results -> {
                    final List<WebSearchOrganicResult> organicResults = results.stream()
                            .map(DuckDuckGoWebSearchEngine::toWebSearchOrganicResult)
                            .collect(toList());

                    return WebSearchResults.from(
                            WebSearchInformationResult.from((long) organicResults.size()), organicResults);
                });
    }

    public static DuckDuckGoWebSearchEngineBuilder builder() {
        return new DuckDuckGoWebSearchEngineBuilder();
    }

    private static WebSearchOrganicResult toWebSearchOrganicResult(DuckDuckGoSearchResult result) {
        return WebSearchOrganicResult.from(result.getTitle(), URI.create(result.getUrl()), result.getSnippet(), null);
    }

    public static class DuckDuckGoWebSearchEngineBuilder {
        private Duration timeout = DEFAULT_TIMEOUT;
        private HttpClient httpClient;

        DuckDuckGoWebSearchEngineBuilder() {}

        public DuckDuckGoWebSearchEngineBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public DuckDuckGoWebSearchEngineBuilder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public DuckDuckGoWebSearchEngine build() {
            if (httpClient == null) {
                throw new IllegalArgumentException("HttpClient is required");
            }

            return new DuckDuckGoWebSearchEngine(this.timeout, this.httpClient);
        }

        public String toString() {
            return "DuckDuckGoWebSearchEngine.DuckDuckGoWebSearchEngineBuilder(timeout=" + this.timeout + ")";
        }
    }
}
