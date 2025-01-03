package dev.langchain4j.web.search.brave;

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

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import static java.time.Duration.ofSeconds;

/**
 * An implementation of a{@link WebSearchEngine} that uses
 * <a href="https://brave.com/search/api//">Brave Search API</a> for performing web searches.
 */
@Builder
class BraveWebSearchEngine implements WebSearchEngine {
    /**
     * Constructs a new BraveSearchEngine with the specified parameters.
     *
     * @param apiKey        the Brave Search API key to access the Brave Search API
     * <p>The API key can be generated at <a href="https://brave.com/search/api/">here</a></p>
     * @param count         the parameter that defines the number of web search responses
     * @param safeSearch    Filters search results for adult content:
     * The following values are supported:
     * - off: No filtering is done.
     * - moderate: Filters explicit content, like images and videos, but allows adult domains in the search results.
     * - strict: Drops all adult content from search results.
     * @param resultFilter  Available result filter values are:
     * - summarizer
     * - web (set to default as web)
     * - image
     * Example result filter param:
     * result_filter=discussions,videos returns only discussions and videos responses.
     * Another example where only location results are required, set the result_filter param to result_filter=locations.
     * @param freshness     Filters search results by when they were discovered.
     * The following values are supported:
     * - pd: Discovered within the last 24 hours.
     * - pw: Discovered within the last 7 days.
     * - pm: Discovered within the last 31 days.
     * - py: Discovered within the last 365 days.
     * - YYYY-MM-DDtoYYYY-MM-DD: timeframe is also supported by specifying the date range
     * e.g., 2022-04-01to2022-07-30.
     */

    private static final String DEFAULT_BASE_URL = "https://api.search.brave.com/";
    private final String apiKey;
    private final BraveClient braveClient;
    private final Map<String, Object> optionalParams;

    /**
     * This constructor has a  Builder method for building Brave Search Client with the default parameters:
     * baseUrl : It acts as the base URL where the requests are sent - a trailing route /search is added by the retrofit while request is made by user
     * timeout : It is the maximum amount of time a network request is allowed to wait for a response from the server before it is considered a failure
     */

    public BraveWebSearchEngine(String baseUrl,
                                String apiKey,
                                Duration timeout,
                                Map<String, Object> optionalParams
    ) {
        this.braveClient = BraveClient.builder()
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .timeout(getOrDefault(timeout, ofSeconds(10)))
                .build();
        this.optionalParams = getOrDefault(copyIfNotNull(optionalParams), new HashMap<>());
        this.apiKey = ensureNotBlank(apiKey, "apiKey");
    }

    @Override
    public WebSearchResults search(final WebSearchRequest webSearchRequest) {

        BraveWebSearchRequest request = BraveWebSearchRequest.builder()
                .apiKey(apiKey)
                .query(webSearchRequest.searchTerms())
                .optionalParams(optionalParams)
                .build();
        BraveWebSearchResponse response = braveClient.search(request);

        List<WebSearchOrganicResult> results = response.getWeb().getResults().stream()
                .map(BraveWebSearchEngine::toOrganicResults)
                .toList();

        return WebSearchResults.from(WebSearchInformationResult.from((long)results.size()), results);
    }

    static WebSearchOrganicResult toOrganicResults(BraveWebSearchResponse.Web.Result response) {
        return WebSearchOrganicResult.from(response.getTitle(),
                URI.create(response.getUrl().trim().replace(" ","+")),
                response.getDescription(),
                null);
    }

    static WebSearchEngine withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }
}
