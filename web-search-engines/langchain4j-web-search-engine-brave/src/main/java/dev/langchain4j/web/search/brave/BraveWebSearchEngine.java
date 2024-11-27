package dev.langchain4j.web.search.brave;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;

import java.time.Duration;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import static java.time.Duration.ofSeconds;

/**
 * An implementation of a{@link WebSearchEngine} that uses
 * <a href="https://brave.com/search/api//">Brave Search API</a> for performing web searches.
 */
public class BraveWebSearchEngine implements WebSearchEngine {

    private final BraveClient braveClient;

    /**
     * Constructs a new BraveSearchEngine with the specified parameters.
     *
     * @param apiKey        the Brave Search API key to access the Brave Search API
     *                      <p>The API key can be generated at <a href="https://brave.com/search/api/">here</a></p>
     *
     * @param count         the parameter that defines the number of web search responses
     *
     * @param safeSearch    Filters search results for adult content:
     *                      The following values are supported:
     *                      - off: No filtering is done.
     *                      - moderate: Filters explicit content, like images and videos, but allows adult domains in the search results.
     *                      - strict: Drops all adult content from search results.
     *
     * @param resultFilter  Available result filter values are:
     *                      - discussions
     *                      - faq
     *                      - infobox
     *                      - news
     *                      - query
     *                      - summarizer
     *                      - videos
     *                      - web (set to default as web)
     *                      - locations
     *                      Example result filter param:
     *                      result_filter=discussions,videos returns only discussions and videos responses.
     *                      Another example where only location results are required, set the result_filter param to result_filter=locations.
     *
     * @param freshness     Filters search results by when they were discovered.
     *                      The following values are supported:
     *                      - pd: Discovered within the last 24 hours.
     *                      - pw: Discovered within the last 7 days.
     *                      - pm: Discovered within the last 31 days.
     *                      - py: Discovered within the last 365 days.
     *                      - YYYY-MM-DDtoYYYY-MM-DD: timeframe is also supported by specifying the date range
     *                        e.g., 2022-04-01to2022-07-30.
     *
     */

    private static final String DEFAULT_BASE_URL = "https://api.search.brave.com/";
    private final String apiKey;
    private final Integer count;
    private final String safeSearch;
    private final String resultFilter;
    private final String freshness;


    /**
     * This constructor has a  Builder method for building Brave Search Client with the default parameters:
     *      baseUrl : It acts as the base URL where the requests are sent - a trailing route /search is added by the retrofit while request is made by user
     *      timeout : It is the maximum amount of time a network request is allowed to wait for a response from the server before it is considered a failure
     */

    public BraveWebSearchEngine(String baseUrl,
                                String apiKey,
                                Duration timeout,
                                Integer count,
                                String safeSearch,
                                String resultFilter,
                                String freshness) {

        this.braveClient = BraveClient.builder()
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .timeout(getOrDefault(timeout, ofSeconds(10)))
                .build();

        this.apiKey = ensureNotBlank(apiKey, "apiKey");
        this.count = count;
        this.safeSearch = safeSearch;
        this.resultFilter = resultFilter;
        this.freshness = freshness;
    }

    @Override
    public WebSearchResults search(final String query) {
        return search(WebSearchRequest.builder()
                .searchTerms(query)
                .build());
    }

    @Override
    public WebSearchResults search(final WebSearchRequest webSearchRequest) {
        BraveWebSearchRequest braveWebSearchRequest = BraveWebSearchRequest.builder()
                .apiKey(apiKey)
                .count(count)
                .safeSearch(safeSearch)
                .resultFilter(resultFilter)
                .freshness(freshness)
                .query(webSearchRequest.searchTerms())
                .build();
        BraveResponse response = braveClient.search(braveWebSearchRequest);
        System.out.println("Full Response: " + response);
        if (response.getWeb() != null && response.getWeb().getResults() != null) {
            response.getWeb().getResults().forEach(result -> {
                System.out.println("Title: " + result.getTitle());
                System.out.println("URL: " + result.getUrl());
                System.out.println("Description: " + result.getDescription());
            });
        }

        //todo : refining the output response to find the web results

        return null;
    }
}
