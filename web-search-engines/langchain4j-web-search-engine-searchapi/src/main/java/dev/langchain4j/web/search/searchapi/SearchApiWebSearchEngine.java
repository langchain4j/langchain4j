package dev.langchain4j.web.search.searchapi;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;

/**
 * An implementation of a {@link WebSearchEngine} that uses
 * <a href="https://www.searchapi.io">Search API</a> for performing web searches. <p>
 * Search API supports not only Google Search, but many other engines
 */
public class SearchApiWebSearchEngine implements WebSearchEngine {

    private static final String DEFAULT_BASE_URL = "https://www.searchapi.io";
    private static final String DEFAULT_ENGINE = "google";

    private final String apiKey;
    private final String engine;
    private final SearchApiClient client;
    private final Map<String, Object> optionalParameters;

    /**
     * @param apiKey             Required - the Search API key for accessing their API
     * @param baseUrl            overrides the default SearchApi base url
     * @param timeout            the timeout duration for API requests
     *                           <p>
     *                           Default value is 30 seconds.
     * @param engine             the engine used by Search API to execute the search
     *                           <p>
     *                           Default engine is Google Search.
     * @param optionalParameters parameters to be passed on every request of this the engine, they can be overridden by the WebSearchRequest additional parameters for matching keys
     *                           <p>
     *                           Check <a href="https://www.searchapi.io">Search API</a> for more information on available parameters for each engine
     */
    public SearchApiWebSearchEngine(String apiKey,
                                    String baseUrl,
                                    Duration timeout,
                                    String engine,
                                    Map<String, Object> optionalParameters) {
        this.apiKey = ensureNotBlank(apiKey, "apiKey");
        this.engine = getOrDefault(engine, DEFAULT_ENGINE);
        this.optionalParameters = getOrDefault(copyIfNotNull(optionalParameters), new HashMap<>());
        this.client = SearchApiClient.builder()
                .timeout(getOrDefault(timeout, ofSeconds(30)))
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .build();
    }

    public static SearchApiWebSearchEngineBuilder builder() {
        return new SearchApiWebSearchEngineBuilder();
    }

    /**
     * @param webSearchRequest Check <a href="https://www.searchapi.io">Search API</a> for more information on available additional
     *                         parameters for each engine that can be inside the request
     */
    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {
        SearchApiWebSearchRequest request = SearchApiWebSearchRequest.builder()
                .apiKey(apiKey)
                .engine(engine)
                .query(webSearchRequest.searchTerms())
                .optionalParameters(optionalParameters)
                .additionalRequestParameters(webSearchRequest.additionalParams())
                .build();
        SearchApiWebSearchResponse response = client.search(request);
        return toWebSearchResults(response);
    }

    private WebSearchResults toWebSearchResults(SearchApiWebSearchResponse response) {
        List<OrganicResult> organicResults = response.getOrganicResults();
        Long totalResults = getTotalResults(response.getSearchInformation());
        WebSearchInformationResult searchInformation = WebSearchInformationResult.from(
                totalResults,
                getCurrentPage(response.getPagination()),
                null
        );
        Map<String, Object> searchMetadata = getOrDefault(response.getSearchParameters(), new HashMap<>());
        addToMetadata(searchMetadata, response.getSearchMetadata());
        return WebSearchResults.from(
                searchMetadata,
                searchInformation,
                toWebSearchOrganicResults(organicResults));
    }

    private static long getTotalResults(Map<String, Object> searchInformation) {
        if (searchInformation != null && searchInformation.containsKey("total_results")) {
            Object totalResults = searchInformation.get("total_results");
            return totalResults instanceof Integer
                    ? ((Integer) totalResults).longValue()
                    : (Long) totalResults; // changes depending on the amount of total_results
        }
        return 0;
    }

    private Integer getCurrentPage(Map<String, Object> pagination) {
        if (pagination != null && pagination.containsKey("current")) {
            return (Integer) pagination.get("current");
        }
        return null;
    }

    private void addToMetadata(Map<String, Object> metadata, Map<String, Object> dataToAdd) {
        if (dataToAdd != null) {
            metadata.putAll(dataToAdd);
        }
    }

    private List<WebSearchOrganicResult> toWebSearchOrganicResults(List<OrganicResult> organicResults) {
        return organicResults.stream()
                .map(result -> {
                    Map<String, String> metadata = new HashMap<>(2);
                    metadata.put("position", result.getPosition());
                    return WebSearchOrganicResult.from(
                            result.getTitle(),
                            URI.create(result.getLink()),
                            getOrDefault(result.getSnippet(), ""),
                            null,  // by default google custom search api does not return content
                            metadata);
                })
                .collect(Collectors.toList());
    }

    public static WebSearchEngine withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    public static class SearchApiWebSearchEngineBuilder {
        private String apiKey;
        private String baseUrl;
        private Duration timeout;
        private String engine;
        private Map<String, Object> optionalParameters;

        SearchApiWebSearchEngineBuilder() {
        }

        public SearchApiWebSearchEngineBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public SearchApiWebSearchEngineBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public SearchApiWebSearchEngineBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public SearchApiWebSearchEngineBuilder engine(String engine) {
            this.engine = engine;
            return this;
        }

        public SearchApiWebSearchEngineBuilder optionalParameters(Map<String, Object> optionalParameters) {
            this.optionalParameters = optionalParameters;
            return this;
        }

        public SearchApiWebSearchEngine build() {
            return new SearchApiWebSearchEngine(this.apiKey, this.baseUrl, this.timeout, this.engine, this.optionalParameters);
        }

        public String toString() {
            return "SearchApiWebSearchEngine.SearchApiWebSearchEngineBuilder(apiKey=" + this.apiKey + ", baseUrl=" + this.baseUrl + ", timeout=" + this.timeout + ", engine=" + this.engine + ", optionalParameters=" + this.optionalParameters + ")";
        }
    }
}
