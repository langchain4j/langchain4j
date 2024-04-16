package dev.langchain4j.web.search.google.customsearch;

import com.google.api.client.json.GenericJson;
import com.google.api.services.customsearch.v1.model.Result;
import com.google.api.services.customsearch.v1.model.Search;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import lombok.Builder;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

import static com.google.api.services.customsearch.v1.model.Search.*;
import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.*;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * An implementation of a {@link WebSearchEngine} that uses
 * <a href="https://programmablesearchengine.google.com/">Google Custom Search API</a> for performing web searches.
 */
public class GoogleCustomWebSearchEngine implements WebSearchEngine {

    private final GoogleCustomSearchApiClient googleCustomSearchApiClient;

    /**
     * Constructs a new GoogleCustomWebSearchEngine with the specified parameters.
     *
     * @param apiKey              the Google Search API key for accessing the Google Custom Search API
     *                            <p>
     *                            You can just generate an API key <a href="https://developers.google.com/custom-search/docs/paid_element#api_key">here</a>
     * @param csi                 the Custom Search ID parameter for search the entire web
     *                            <p>
     *                            You can create a Custom Search Engine <a href="https://cse.google.com/cse/create/new">here</a>
     * @param siteRestrict        if your Search Engine is restricted to only searching specific sites, you can set this parameter to true.
     *                            <p>
     *                            Default value is false. View the documentation for more information <a href="https://developers.google.com/custom-search/v1/site_restricted_api">here</a>
     * @param timeout             the timeout duration for API requests
     *                            <p>
     *                            Default value is 60 seconds.
     * @param logRequestResponse  whether to log API request and response
     *                            <p>
     *                            Default value is false.
     * @param maxRetries          the maximum number of retries for API requests
     *                            <p>
     *                            Default value is 10.
     */
    @Builder
    public GoogleCustomWebSearchEngine(String apiKey,
                                       String csi,
                                       Boolean siteRestrict,
                                       Duration timeout,
                                       Boolean logRequestResponse,
                                       Integer maxRetries) {

        this.googleCustomSearchApiClient = GoogleCustomSearchApiClient.builder()
                .apiKey(apiKey)
                .csi(csi)
                .siteRestrict(getOrDefault(siteRestrict,false))
                .timeout(getOrDefault(timeout,Duration.ofSeconds(60)))
                .logRequestResponse(getOrDefault(logRequestResponse,false))
                .maxRetries(getOrDefault(maxRetries,10))
                .build();
    }

    /**
     * Creates a new builder for constructing a GoogleCustomWebSearchEngine with the specified API key and Custom Search ID.
     *
     * @param apiKey  the API key for accessing the Google Custom Search API
     * @param csi     the Custom Search ID parameter for search the entire web
     * @return a new builder instance
     */
    public static GoogleCustomWebSearchEngine withApiKeyAndCsi(String apiKey, String csi){
        return GoogleCustomWebSearchEngine.builder().apiKey(apiKey).csi(csi).build();
    }

    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {
        ensureNotNull(webSearchRequest, "webSearchRequest");

        Queries.Request requestQuery = new Queries.Request();
        requestQuery.setSearchTerms(webSearchRequest.searchTerms());
        requestQuery.setCount(getOrDefault(webSearchRequest.maxResults(),10));
        requestQuery.setGl(webSearchRequest.geoLocation());
        requestQuery.setLanguage(webSearchRequest.language());
        requestQuery.setStartPage(webSearchRequest.startPage());
        requestQuery.setStartIndex(webSearchRequest.startIndex());
        requestQuery.setSafe(webSearchRequest.safeSearch()?"active":"off");
        webSearchRequest.additionalParams().forEach(requestQuery::set);

        Search search = googleCustomSearchApiClient.searchResults(requestQuery);
        return WebSearchResults.from(
                search.getContext()
                , WebSearchInformationResult.from(
                        Long.valueOf(getOrDefault(search.getSearchInformation().getTotalResults(),"0")),
                        !isNullOrEmpty(search.getQueries().getRequest())
                                ?calculatePageNumberFromQueries(search.getQueries().getRequest().get(0)):1,
                        toSearchInforationMap(search.getSearchInformation()))
                , search.getItems().stream()
                        .map(result -> WebSearchOrganicResult.from(
                                    result.getTitle(),
                                    URI.create(result.getLink()),
                                    result.getSnippet(),
                                    null, // by default google custom search api does not return content
                                    toResultMetadataMap(result)
                        )).collect(toList()));
    }

    private static Map<String, Object> toSearchInforationMap(SearchInformation searchInfo) {
        return Stream.of(new Object[][] {
                {"searchTime", searchInfo.getSearchTime()},
                {"formattedSearchTime", searchInfo.getFormattedSearchTime()},
                {"formattedTotalResults", searchInfo.getFormattedTotalResults()}
        }).collect(toMap(data -> (String) data[0], data -> data[1]));
    }

    private static Map<String, String> toResultMetadataMap(Result result) {
        return Stream.of(new Object[][] {
                {"cacheId", getOrDefault(result.getCacheId(),"")},
                {"displayLink", getOrDefault(result.getDisplayLink(),"")},
                {"fileFormat", getOrDefault(result.getFileFormat(),"")},
                {"htmlTitle", getOrDefault(result.getHtmlTitle(),"")},
                {"htmlSnippet", getOrDefault(result.getHtmlSnippet(),"")},
                {"kind", getOrDefault(result.getKind(),"")},
                {"formattedUrl", getOrDefault(result.getFormattedUrl(),"")},
                {"htmlFormattedUrl", getOrDefault(result.getHtmlFormattedUrl(),"")},
                {"pagemap", getOrDefault(result.getPagemap(),"")!=""?result.getPagemap().toString():""}
        }).collect(toMap(data -> (String) data[0], data -> (String) data[1]));
    }

    private static Integer calculatePageNumberFromQueries(GenericJson query) {
        if (query instanceof Queries.PreviousPage) {
            Queries.PreviousPage previousPage = (Queries.PreviousPage) query;
            return calculatePageNumber(previousPage.getStartIndex());
        }
        if (query instanceof Queries.Request) {
            Queries.Request currentPage = (Queries.Request) query;
            return calculatePageNumber(getOrDefault(currentPage.getStartIndex(),1));
        }
        if (query instanceof Queries.NextPage) {
            Queries.NextPage nextPage = (Queries.NextPage) query;
            return calculatePageNumber(nextPage.getStartIndex());
        }
        return null;
    }

    private static Integer calculatePageNumber(Integer startIndex) {
        if (startIndex == null)
            return null;
        return ((startIndex -1) / 10) + 1;
    }
}
