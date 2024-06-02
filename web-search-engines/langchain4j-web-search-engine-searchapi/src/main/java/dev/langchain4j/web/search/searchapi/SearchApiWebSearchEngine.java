package dev.langchain4j.web.search.searchapi;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import lombok.Builder;

import java.time.Duration;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;

/**
 * An implementation of a {@link WebSearchEngine} that uses
 * <a href="https://www.searchapi.io">Search API</a> for performing web searches. <p>
 * Search API supports not only Google Search, but many other engines, available implementations for Langchain4j
 * can be found in {@link SearchApiEngine}
 */
public class SearchApiWebSearchEngine implements WebSearchEngine {

    private static final String BASE_URL = "https://www.searchapi.io";
    private static final SearchApiEngine DEFAULT_ENGINE = SearchApiEngine.GOOGLE_SEARCH;

    private final String apiKey;
    private final SearchApiEngine engine;
    private final SearchApiClient client;
    private final SearchApiRequestResponseHandler handler;

    /**
     * @param apiKey        the Search API key for accessing their API
     * @param timeout       the timeout duration for API requests
     *                      <p>
     *                      Default value is 10 seconds.
     * @param engine        the engine used by Search API to execute the search
     *                      <p>
     *                      Default engine is Google Search.
     */
    @Builder
    public SearchApiWebSearchEngine(String apiKey,
                                    Duration timeout,
                                    SearchApiEngine engine) {
        this.apiKey = ensureNotBlank(apiKey, "apiKey");
        this.engine = getOrDefault(engine, DEFAULT_ENGINE);
        this.handler = RequestResponseHandlerFactory.create(this.engine);
        this.client = SearchApiClient.builder()
                .baseUrl(BASE_URL)
                .timeout(getOrDefault(timeout, ofSeconds(10)))
                .build();
    }

    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {
        Map<String, Object> additionalParameters = handler.getAdditionalParams(webSearchRequest);
        SearchApiRequest request = SearchApiRequest.builder()
                .apiKey(apiKey)
                .engine(engine)
                .query(webSearchRequest.searchTerms())
                .additionalParameters(additionalParameters)
                .build();
        SearchApiResponse response = client.search(request);
        return handler.getWebSearchResult(response);
    }

    public static WebSearchEngine withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }
}
