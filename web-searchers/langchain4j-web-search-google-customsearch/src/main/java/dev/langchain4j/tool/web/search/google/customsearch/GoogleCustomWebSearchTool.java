package dev.langchain4j.tool.web.search.google.customsearch;

import com.google.api.services.customsearch.v1.model.Result;
import com.google.api.services.customsearch.v1.model.Search;
import dev.langchain4j.data.web.WebResult;
import dev.langchain4j.tool.web.search.WebSearchTool;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.stream.Collectors.toList;

/**
 * An implementation of a {@link WebSearchTool} that uses
 * <a href="https://programmablesearchengine.google.com/">Google Custom Search API</a> for performing web searches.
 */
public class GoogleCustomWebSearchTool implements WebSearchTool {

    private final GoogleCustomSearchApiClient googleCustomSearchApiClient;
    private final String geoLocation;
    private final Integer numberOfPage;
    private final Integer maxResults;
    private final String languageRestricted;
    private final String dateRestrict;
    private final Boolean safe;

    /**
     * Constructs a new GoogleCustomWebSearchTool with the specified parameters.
     *
     * @param apiKey              the Google Search API key for accessing the Google Custom Search API
     *                            <p>
     *                            You can just generate an API key <a href="https://developers.google.com/custom-search/docs/paid_element#api_key">here</a>
     * @param csi                 the Custom Search ID parameter for search the entire web
     *                            <p>
     *                            You can create a Custom Search Engine <a href="https://cse.google.com/cse/create/new">here</a>
     * @param maxResults          the maximum number of search results to retrieve
     *                            <p>
     *                            Default value is 5. The maximum number of search results that can be retrieved is 10
     * @param numberOfPage        the page number of the search results to retrieve
     * @param siteRestrict        if your Search Engine is restricted to only searching specific sites, you can set this parameter to true.
     *                            <p>
     *                            Default value is false. View the documentation for more information <a href="https://developers.google.com/custom-search/v1/site_restricted_api">here</a>
     * @param geoLocation         the two-letter country code for geolocation restriction.
     *                            <p>
     *                            See the Country Codes <a href="https://developers.google.com/custom-search/docs/json_api_reference#countryCodes">page</a> for a list of valid values.
     * @param languageRestricted  restricts the search to documents written in a particular language (e.g., lr=lang_en).
     * @param safe                whether to enable search safety level.
     * @param dateRestrict        restricts results to URLs based on date. Supported values include: d[number], w[number], m[number], y[number].
     *                            <p>
     *                            Default value is last two weeks: w[2]. View the documentation for more information <a href="https://support.google.com/programmable-search/thread/208446614?hl=en&sjid=12279488955888720832-NC">here</a>
     * @param timeout             the timeout duration for API requests
     * @param logResponses        whether to log API request and response
     * @param maxRetries          the maximum number of retries for API requests
     */
    @Builder
    public GoogleCustomWebSearchTool(String apiKey,
                                     String csi,
                                     Integer maxResults,
                                     Integer numberOfPage,
                                     Boolean siteRestrict,
                                     String geoLocation,
                                     String languageRestricted,
                                     Boolean safe,
                                     String dateRestrict,
                                     Duration timeout,
                                     Boolean logResponses,
                                     Integer maxRetries) {

        this.googleCustomSearchApiClient = GoogleCustomSearchApiClient.builder()
                .apiKey(apiKey)
                .csi(csi)
                .siteRestrict(getOrDefault(siteRestrict,false))
                .timeout(getOrDefault(timeout,Duration.ofSeconds(60)))
                .maxRetries(getOrDefault(maxRetries,10))
                .logRequestResponse(getOrDefault(logResponses,false))
                .build();
        this.geoLocation = getOrDefault(geoLocation,"");
        this.languageRestricted = languageRestricted;
        this.safe = getOrDefault(safe,true);
        this.numberOfPage = getDefaultNaturalNumber(getOrDefault(numberOfPage, 1));
        this.maxResults = maxResultsAllowed(getDefaultNaturalNumber(getOrDefault(maxResults, 5)));
        this.dateRestrict = getOrDefault(dateRestrict,"w[2]");
    }

    private static Integer maxResultsAllowed(Integer maxResults){
        return maxResults > 10 ? 10 : maxResults;
    }

    private static Integer calculateIndexStartPage(Integer pageNumber) {
        return ((pageNumber -1) * 10) + 1;
    }

    private static Integer getDefaultNaturalNumber(Integer number){
        return number > 0 ? number : 1;
    }

    /**
     * Creates a new builder for constructing a GoogleCustomWebSearchTool with the specified API key and Custom Search ID.
     *
     * @param apiKey  the API key for accessing the Google Custom Search API
     * @param csi     the Custom Search ID parameter for search the entire web
     * @return a new builder instance
     */
    public static GoogleCustomWebSearchTool withApiKeyAndCsi(String apiKey, String csi){
        return GoogleCustomWebSearchTool.builder().apiKey(apiKey).csi(csi).build();
    }

    @Override
    public List<WebResult> searchResults(String query) {
        ensureNotBlank(query, "query");

        Search.Queries.Request requestQuery = new Search.Queries.Request();
        requestQuery.setExactTerms(query);
        requestQuery.setCount(this.maxResults);
        requestQuery.setStartIndex(calculateIndexStartPage(this.numberOfPage));
        requestQuery.setGl(this.geoLocation);
        requestQuery.setSafe(this.safe?"active":"off");
        requestQuery.setLanguage(this.languageRestricted);
        requestQuery.setDateRestrict(this.dateRestrict);

        List<Result> results = googleCustomSearchApiClient.searchResults(requestQuery);
        return results.stream()
                .map(result -> WebResult.from(
                        result.getTitle(),
                        result.getSnippet(),
                        result.getLink()))
                .collect(toList());
    }
}
