package dev.langchain4j.web.search.searchapi;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import lombok.Builder;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;

/**
 * An implementation of a {@link WebSearchEngine} that uses
 * <a href="https://www.searchapi.io">Search API</a> for performing web searches. <p>
 * Search API supports not only Google Search, but many other engines
 */
public class SearchApiWebSearchEngine implements WebSearchEngine {

    private static final String BASE_URL = "https://www.searchapi.io";
    private static final String DEFAULT_ENGINE = "google";

    private final String apiKey;
    private final String engine;
    private final SearchApiClient client;
    private final Map<String, Object> optionalParameters;

    /**
     * @param apiKey             the Search API key for accessing their API
     * @param timeout            the timeout duration for API requests
     *                           <p>
     *                           Default value is 30 seconds.
     * @param engine             the engine used by Search API to execute the search
     *                           <p>
     *                           Default engine is Google Search.
     * @param optionalParameters optional parameters to be passed on every request of this the engine, they can be overridden by the WebSearchRequest additional parameters for matching keys
     *                           <p>
     *                           Check <a href="https://www.searchapi.io">Search API</a> for more information on available parameters for each engine
     */
    @Builder
    public SearchApiWebSearchEngine(String apiKey,
                                    Duration timeout,
                                    String engine,
                                    Map<String, Object> optionalParameters) {
        this.apiKey = ensureNotBlank(apiKey, "apiKey");
        this.engine = getOrDefault(engine, DEFAULT_ENGINE);
        this.optionalParameters = getOrDefault(optionalParameters, new HashMap<>());
        this.client = SearchApiClient.builder()
                .baseUrl(BASE_URL)
                .timeout(getOrDefault(timeout, ofSeconds(30)))
                .build();
    }

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
        Map<String, Object> searchInformationMetadata = getOrDefault(response.getSearchInformation(), new HashMap<>());
        Long totalResults = ((Integer) organicResults.size()).longValue(); // not ideal, but it may not be present in the response and is required not null by WebSearchInformationResult
        WebSearchInformationResult searchInformation = WebSearchInformationResult.from(
                totalResults,
                getCurrentPage(response.getPagination()),
                searchInformationMetadata
        );
        Map<String, Object> searchMetadata = getOrDefault(response.getSearchParameters(), new HashMap<>());
        addToMetadata(searchMetadata, response.getPagination());
        addToMetadata(searchMetadata, searchInformationMetadata);
        return WebSearchResults.from(
                searchMetadata,
                searchInformation,
                toWebSearchOrganicResults(organicResults));
    }

    private Integer getCurrentPage(Map<String, Object> pagination) {
        if (pagination != null && pagination.containsKey("current")) {
            return ((Double) pagination.get("current")).intValue();
        }
        return null;
    }

    private void addToMetadata(Map<String, Object> searchInformationMetadata, Map<String, Object> pagination) {
        if (pagination != null) {
            searchInformationMetadata.putAll(pagination);
        }
    }

    private List<WebSearchOrganicResult> toWebSearchOrganicResults(List<OrganicResult> organicResults) {
        return organicResults.stream()
                .map(result -> WebSearchOrganicResult.from(
                        result.getTitle(),
                        URI.create(result.getLink()),
                        getOrDefault(result.getSnippet(), ""),
                        null,  // by default google custom search api does not return content
                        null
                ))
                .collect(Collectors.toList());
    }

    public static WebSearchEngine withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }
}
