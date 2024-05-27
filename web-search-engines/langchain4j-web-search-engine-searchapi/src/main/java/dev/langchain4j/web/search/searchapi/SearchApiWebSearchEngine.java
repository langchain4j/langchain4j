package dev.langchain4j.web.search.searchapi;

import dev.langchain4j.web.search.*;
import lombok.Builder;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

/**
 * Represents SearchApi Search API as a {@code WebSearchEngine}.
 * See more details <a href="https://docs.searchapi.io/docs/searchapi-api/rest_api">here</a>.
 * <br>
 * When {@link #includeRawContent} is set to {@code true},
 * the raw content will appear in the {@link WebSearchOrganicResult#content()} field of each result.
 * <br>
 * When {@link #includeAnswer} is set to {@code true},
 * the answer will appear in the {@link WebSearchOrganicResult#snippet()} field of the first result.
 * In this case, the {@link WebSearchOrganicResult#url()} of the first result will always be "https://searchapi.io/" and
 * the {@link WebSearchOrganicResult#title()} will always be "SearchApi Search API".
 */
public class SearchApiWebSearchEngine implements WebSearchEngine {

    private static final String DEFAULT_BASE_URL = "https://api.tavily.com";

    private final String apiKey;
    private final SearchApiClient searchapiClient;
    private final String searchDepth;
    private final Boolean includeAnswer;
    private final Boolean includeRawContent;
    private final List<String> includeDomains;
    private final List<String> excludeDomains;

    @Builder
    public SearchApiWebSearchEngine(String baseUrl,
                                 String apiKey,
                                 Duration timeout,
                                 String searchDepth,
                                 Boolean includeAnswer,
                                 Boolean includeRawContent,
                                 List<String> includeDomains,
                                 List<String> excludeDomains) {
        this.searchapiClient = SearchApiClient.builder()
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .timeout(getOrDefault(timeout, ofSeconds(10)))
                .build();
        this.apiKey = ensureNotBlank(apiKey, "apiKey");
        this.searchDepth = searchDepth;
        this.includeAnswer = includeAnswer;
        this.includeRawContent = includeRawContent;
        this.includeDomains = copyIfNotNull(includeDomains);
        this.excludeDomains = copyIfNotNull(excludeDomains);
    }

    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {

        SearchApiSearchRequest request = SearchApiSearchRequest.builder()
                .apiKey(apiKey)
                .query(webSearchRequest.searchTerms())
                .searchDepth(searchDepth)
                .includeAnswer(includeAnswer)
                .includeRawContent(includeRawContent)
                .maxResults(webSearchRequest.maxResults())
                .includeDomains(includeDomains)
                .excludeDomains(excludeDomains)
                .build();

        SearchApiResponse searchapiResponse = searchapiClient.search(request);

        final List<WebSearchOrganicResult> results = searchapiResponse.getResults().stream()
                .map(SearchApiWebSearchEngine::toWebSearchOrganicResult)
                .collect(toList());

        if (searchapiResponse.getAnswer() != null) {
            WebSearchOrganicResult answerResult = WebSearchOrganicResult.from(
                    "SearchApi Search API",
                    URI.create("https://searchapi.io/"),
                    searchapiResponse.getAnswer(),
                    null
            );
            results.add(0, answerResult);
        }

        return WebSearchResults.from(WebSearchInformationResult.from((long) results.size()), results);
    }

    public static SearchApiWebSearchEngine withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    private static WebSearchOrganicResult toWebSearchOrganicResult(SearchApiSearchResult searchapiSearchResult) {
        return WebSearchOrganicResult.from(searchapiSearchResult.getTitle(),
                URI.create(searchapiSearchResult.getUrl()),
                searchapiSearchResult.getContent(),
                searchapiSearchResult.getRawContent(),
                Collections.singletonMap("score", String.valueOf(searchapiSearchResult.getScore())));
    }
}

