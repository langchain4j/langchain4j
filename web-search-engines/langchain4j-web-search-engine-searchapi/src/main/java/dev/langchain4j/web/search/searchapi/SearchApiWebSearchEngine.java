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

    /**
     * @param apiKey  the Search API key for accessing their API
     * @param timeout the timeout duration for API requests
     *                <p>
     *                Default value is 30 seconds.
     * @param engine  the engine used by Search API to execute the search
     *                <p>
     *                Default engine is Google Search.
     */
    @Builder
    public SearchApiWebSearchEngine(String apiKey,
                                    Duration timeout,
                                    String engine) {
        this.apiKey = ensureNotBlank(apiKey, "apiKey");
        this.engine = getOrDefault(engine, DEFAULT_ENGINE);
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
                .additionalParameters(webSearchRequest.additionalParams())
                .build();
        SearchApiWebSearchResponse response = client.search(request);
        return toWebSearchResults(response);
    }

    private WebSearchResults toWebSearchResults(SearchApiWebSearchResponse response) {
        List<OrganicResult> organicResults = response.getOrganicResults();
        Long totalResults = toTotalResults(response);
        Map<String, Object> searchInformationMetadata = toSearchInformationMetadata(response);
        WebSearchInformationResult searchInformation = WebSearchInformationResult.from(
                totalResults,
                getCurrentPage(response.getPagination()),
                searchInformationMetadata
        );
        Map<String, Object> searchMetadata = toSearchMetadata(response);
        return WebSearchResults.from(
                searchMetadata,
                searchInformation,
                toWebSearchOrganicResults(organicResults));
    }

    private Long toTotalResults(SearchApiWebSearchResponse response) {
        SearchInformation searchInformation = response.getSearchInformation();
        return searchInformation != null && searchInformation.getTotalResults() != null
                ? searchInformation.getTotalResults()
                : response.getOrganicResults().size();
    }

    private Map<String, Object> toSearchInformationMetadata(SearchApiWebSearchResponse response) {
        Map<String, Object> metadata = new HashMap<>();
        Pagination pagination = response.getPagination();
        if (pagination != null) {
            metadata.put("current", pagination.getCurrent());
            metadata.put("next", pagination.getNext());
        }
        return metadata;
    }

    private Integer getCurrentPage(Pagination pagination) {
        return pagination != null ? pagination.getCurrent() : null;
    }

    private Map<String, Object> toSearchMetadata(SearchApiWebSearchResponse response) {
        Map<String, Object> metadata = new HashMap<>();
        SearchParameters searchParameters = response.getSearchParameters();
        if (searchParameters != null) {
            metadata.put("engine", searchParameters.getEngine());
            metadata.put("q", searchParameters.getQ());
        }
        return metadata;
    }

    private List<WebSearchOrganicResult> toWebSearchOrganicResults(List<OrganicResult> organicResults) {
        return organicResults.stream()
                .map(result -> WebSearchOrganicResult.from(
                        result.getTitle(),
                        URI.create(result.getLink()),
                        result.getSnippet(),
                        null,  // by default google custom search api does not return content
                        null
                ))
                .collect(Collectors.toList());
    }

    public static WebSearchEngine withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }
}
