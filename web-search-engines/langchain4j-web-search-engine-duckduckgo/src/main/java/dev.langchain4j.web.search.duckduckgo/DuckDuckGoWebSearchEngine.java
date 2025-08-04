package dev.langchain4j.web.search.duckduckgo;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.net.URI;
import java.time.Duration;
import java.util.List;

public class DuckDuckGoWebSearchEngine implements WebSearchEngine {

    private final DuckDuckGoClient duckDuckGoClient;
    private final String region;
    private final String safeSearch;
    private final String timeLimit;

    public DuckDuckGoWebSearchEngine(Duration timeout, String region, String safeSearch, String timeLimit) {
        this.duckDuckGoClient = DuckDuckGoClient.builder()
                .timeout(getOrDefault(timeout, ofSeconds(30)))
                .build();
        this.region = region;
        this.safeSearch = safeSearch;
        this.timeLimit = timeLimit;
    }

    public static DuckDuckGoWebSearchEngineBuilder builder() {
        return new DuckDuckGoWebSearchEngineBuilder();
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

    public static DuckDuckGoWebSearchEngine create() {
        return builder().build();
    }

    private static WebSearchOrganicResult toWebSearchOrganicResult(DuckDuckGoSearchResult result) {
        return WebSearchOrganicResult.from(result.getTitle(), URI.create(result.getUrl()), result.getSnippet(), null);
    }

    public static class DuckDuckGoWebSearchEngineBuilder {
        private Duration timeout;
        private String region;
        private String safeSearch;
        private String timeLimit;

        DuckDuckGoWebSearchEngineBuilder() {}

        public DuckDuckGoWebSearchEngineBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public DuckDuckGoWebSearchEngineBuilder region(String region) {
            this.region = region;
            return this;
        }

        public DuckDuckGoWebSearchEngineBuilder safeSearch(String safeSearch) {
            this.safeSearch = safeSearch;
            return this;
        }

        public DuckDuckGoWebSearchEngineBuilder timeLimit(String timeLimit) {
            this.timeLimit = timeLimit;
            return this;
        }

        public DuckDuckGoWebSearchEngine build() {
            return new DuckDuckGoWebSearchEngine(this.timeout, this.region, this.safeSearch, this.timeLimit);
        }

        public String toString() {
            return "DuckDuckGoWebSearchEngine.DuckDuckGoWebSearchEngineBuilder(timeout=" + this.timeout + ", region="
                    + this.region + ", safeSearch=" + this.safeSearch + ", timeLimit=" + this.timeLimit + ")";
        }
    }
}
