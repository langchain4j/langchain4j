package dev.langchain4j.web.search.searchapi;

import dev.langchain4j.web.search.*;
import lombok.Builder;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
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

    public static final String DEFAULT_BASE_URL = "https://www.searchapi.io";
    public static final String DEFAULT_ENGINE = "google";

    private final String apiKey;
    private final String engine;
    private final SearchApiClient searchapiClient;
    private final Boolean logRequests;

    @Builder
    public SearchApiWebSearchEngine(final String apiKey,
    							final String engine,
                                final Duration timeout,
                                final Boolean logRequests) {
    	
    	this.apiKey = ensureNotBlank(apiKey, "apiKey");
        this.engine = ensureNotBlank(getOrDefault(engine, DEFAULT_ENGINE), "engine");
    	this.logRequests = logRequests;
    	
        this.searchapiClient = SearchApiClient.builder()
                .baseUrl(DEFAULT_BASE_URL)
                .apiKey(this.apiKey)
                .engine(this.engine)
                .timeout(getOrDefault(timeout, ofSeconds(10)))
                .logRequests(this.logRequests)
                .build();
    }

    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {    	
        final SearchApiSearchRequest.SearchApiSearchRequestBuilder requestBuilder = SearchApiSearchRequest.builder();
        
        requestBuilder
        	.q(webSearchRequest.searchTerms())
        	.safe(webSearchRequest.safeSearch())
            .num(webSearchRequest.maxResults())
            .page(webSearchRequest.startPage());
        
    	/** {@link WebSearchRequest#language} is in a composite string "en-us" but SearchApi splits them into 2 individual strings.
    		See https://github.com/dewitt/opensearch/blob/master/opensearch-1-1-draft-6.md#the-language-element */
    	String hl = null, gl = null;
    	if (isNotNullOrBlank(webSearchRequest.language())) {
    		final String lang[] = webSearchRequest.language().split("-");
    		if (lang.length == 2) {
    			hl = lang[0];
    			gl = lang[1];
    			
    			if (isNotNullOrBlank(hl)) requestBuilder.hl(hl);
    			if (isNotNullOrBlank(gl)) requestBuilder.gl(gl);
    		}
    	}

        final SearchApiSearchRequest request = requestBuilder.build();
        final SearchApiResponse searchapiResponse = searchapiClient.search(request);

//        final List<WebSearchOrganicResult> results = searchapiResponse.getResults().stream()
//                .map(SearchApiWebSearchEngine::toWebSearchOrganicResult)
//                .collect(toList());
//
//        if (searchapiResponse.getAnswer() != null) {
//            WebSearchOrganicResult answerResult = WebSearchOrganicResult.from(
//                    "SearchApi Search API",
//                    URI.create("https://searchapi.io/"),
//                    searchapiResponse.getAnswer(),
//                    null
//            );
//            results.add(0, answerResult);
//        }
//
//        return WebSearchResults.from(WebSearchInformationResult.from((long) results.size()), results);
        return null;
    }

    public static SearchApiWebSearchEngineBuilder withApiKey(String apiKey) {
        return builder().apiKey(apiKey);
    }

    private static WebSearchOrganicResult toWebSearchOrganicResult(SearchApiSearchResult searchapiSearchResult) {
        return WebSearchOrganicResult.from(searchapiSearchResult.getTitle(),
                URI.create(searchapiSearchResult.getUrl()),
                searchapiSearchResult.getContent(),
                searchapiSearchResult.getRawContent(),
                Collections.singletonMap("score", String.valueOf(searchapiSearchResult.getScore())));
    }
}

