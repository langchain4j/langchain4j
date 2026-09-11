package dev.langchain4j.web.search.brave;

import static dev.langchain4j.internal.UriUtils.createUriSafely;
import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents Brave Search API as a {@code WebSearchEngine}.
 * See more details <a href="https://api-dashboard.search.brave.com/documentation">here</a>.
 * <br>
 * The Brave web search endpoint returns at most 20 results per page, so {@link WebSearchRequest#maxResults()}
 * values above 20 are clamped to 20.
 * Pagination is limited to 10 pages, so {@link WebSearchRequest#startPage()} values above 10 are clamped to 10.
 * Whether further results are available can be checked in the
 * {@link WebSearchResults#searchMetadata()} key {@code moreResultsAvailable}.
 * <br>
 * The {@link WebSearchRequest#safeSearch()} flag is mapped to Brave's {@code safesearch} parameter:
 * {@code true} becomes {@code strict} and {@code false} becomes {@code off}.
 * <br>
 * Brave-specific parameters (e.g. {@code freshness}, {@code units}, {@code ui_lang}, {@code goggles})
 * can be passed through {@link WebSearchRequest#additionalParams()}.
 */
public class BraveWebSearchEngine implements WebSearchEngine {

    private static final String DEFAULT_BASE_URL = "https://api.search.brave.com";
    private static final int MAX_COUNT = 20;
    private static final int MAX_OFFSET = 9;

    private final BraveClient braveClient;

    public BraveWebSearchEngine(String baseUrl, String apiKey, Duration timeout) {
        this(builder().baseUrl(baseUrl).apiKey(apiKey).timeout(timeout));
    }

    public BraveWebSearchEngine(BraveWebSearchEngineBuilder builder) {
        this.braveClient = BraveClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(builder.apiKey, "apiKey"))
                .timeout(getOrDefault(builder.timeout, ofSeconds(30)))
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .build();
    }

    public static BraveWebSearchEngineBuilder builder() {
        return new BraveWebSearchEngineBuilder();
    }

    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {
        ensureNotNull(webSearchRequest, "webSearchRequest");

        BraveWebSearchResponse response = braveClient.search(toBraveWebSearchRequest(webSearchRequest));

        List<WebSearchOrganicResult> results = toWebSearchOrganicResults(response);

        Map<String, Object> searchMetadata = new HashMap<>();
        if (response.getQuery() != null && response.getQuery().getMoreResultsAvailable() != null) {
            searchMetadata.put("moreResultsAvailable", response.getQuery().getMoreResultsAvailable());
        }

        return WebSearchResults.from(
                searchMetadata,
                WebSearchInformationResult.from((long) results.size(), webSearchRequest.startPage(), null),
                results);
    }

    static BraveWebSearchRequest toBraveWebSearchRequest(WebSearchRequest webSearchRequest) {
        Integer count = null;
        if (webSearchRequest.maxResults() != null) {
            count = Math.min(Math.max(webSearchRequest.maxResults(), 1), MAX_COUNT);
        }

        Integer offset = null;
        if (webSearchRequest.startPage() != null && webSearchRequest.startPage() > 1) {
            offset = Math.min(webSearchRequest.startPage() - 1, MAX_OFFSET);
        }

        String safesearch = null;
        if (Boolean.TRUE.equals(webSearchRequest.safeSearch())) {
            safesearch = "strict";
        } else if (Boolean.FALSE.equals(webSearchRequest.safeSearch())) {
            safesearch = "off";
        }

        return BraveWebSearchRequest.builder()
                .query(webSearchRequest.searchTerms())
                .count(count)
                .offset(offset)
                .language(webSearchRequest.language())
                .country(webSearchRequest.geoLocation())
                .safesearch(safesearch)
                .additionalParameters(copyIfNotNull(webSearchRequest.additionalParams()))
                .build();
    }

    private static List<WebSearchOrganicResult> toWebSearchOrganicResults(BraveWebSearchResponse response) {
        // Depending on the API version, results are nested under the "web" key or returned at the top level.
        List<BraveSearchResult> braveResults =
                response.getWeb() != null && !isNullOrEmpty(response.getWeb().getResults())
                        ? response.getWeb().getResults()
                        : response.getResults();

        if (isNullOrEmpty(braveResults)) {
            return new ArrayList<>();
        }

        return braveResults.stream()
                .map(BraveWebSearchEngine::toWebSearchOrganicResult)
                .filter(Objects::nonNull)
                .collect(toList());
    }

    private static WebSearchOrganicResult toWebSearchOrganicResult(BraveSearchResult braveResult) {
        // Skip results whose link cannot be resolved into a URI,
        // so a single unresolvable link does not abort the whole search.
        URI url = createUriSafely(braveResult.getUrl());
        if (url == null || isNullOrEmpty(braveResult.getTitle())) {
            return null;
        }

        Map<String, String> metadata = new HashMap<>();
        if (isNotNullOrBlank(braveResult.getPageAge())) {
            metadata.put("page_age", braveResult.getPageAge());
        }

        return WebSearchOrganicResult.from(
                braveResult.getTitle(),
                url,
                braveResult.getDescription(),
                null, // by default brave search api does not return full page content
                metadata);
    }

    public static WebSearchEngine withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    public static class BraveWebSearchEngineBuilder {
        private String baseUrl;
        private String apiKey;
        private Duration timeout;
        private HttpClientBuilder httpClientBuilder;
        private Boolean logRequests;
        private Boolean logResponses;

        BraveWebSearchEngineBuilder() {}

        public BraveWebSearchEngineBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public BraveWebSearchEngineBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public BraveWebSearchEngineBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public BraveWebSearchEngineBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public BraveWebSearchEngineBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public BraveWebSearchEngineBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public BraveWebSearchEngine build() {
            return new BraveWebSearchEngine(this);
        }

        public String toString() {
            return "BraveWebSearchEngine.BraveWebSearchEngineBuilder(baseUrl=" + this.baseUrl + ", apiKey="
                    + (this.apiKey == null ? null : "********") + ", timeout=" + this.timeout + ")";
        }
    }
}
