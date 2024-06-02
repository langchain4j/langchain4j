package dev.langchain4j.web.search.searchapi;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import dev.langchain4j.web.search.searchapi.result.OrganicResult;
import lombok.Builder;

/**
 * Represents SearchApi Search API as a {@code WebSearchEngine}.
 * See more details <a href="https://www.searchapi.io/docs/google">here</a>.
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
    public WebSearchResults search(final WebSearchRequest webSearchRequest) {    	
        ensureNotNull(webSearchRequest, "webSearchRequest");

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
        
        // conduct the search
        final SearchApiResponse searchapiResponse = searchapiClient.search(request);        
        
        final List<WebSearchOrganicResult> results = searchapiResponse.getOrganicResults().stream()
                .map(result -> WebSearchOrganicResult.from(
                        result.getTitle(),
                        URI.create(result.getLink()),
                        result.getSnippet(),
                        null, // searchapi does not return content
                        toResultMetadataMap(result))) 
                .collect(toList());
        
        final Long totalResults = Double.valueOf(searchapiResponse.getSearchInformation().get("total_results").toString()).longValue();
    	final WebSearchInformationResult result = WebSearchInformationResult.from(
    			getOrDefault(totalResults, Long.valueOf(results.size())), 
    			getOrDefault(searchapiResponse.getPagination().getCurrent(), 1), 
    			searchapiResponse.getSearchParameters());
    	
    	// merge the "search_information" JSON element with the "search_metadata" JSON element
    	searchapiResponse.getSearchMetadata().putAll(searchapiResponse.getSearchInformation()); 
        return WebSearchResults.from(searchapiResponse.getSearchMetadata(), result, results);
    }

    public static SearchApiWebSearchEngineBuilder withApiKey(final String apiKey) {
        return builder().apiKey(apiKey);
    }
    
    private static Map<String, String> toResultMetadataMap(OrganicResult result) {
        final Map<String, String> metadata = new HashMap<>();
       
        metadata.put("position", String.valueOf(result.getPosition()));
        metadata.put("source", result.getSource());
        metadata.put("thumbnail", result.getThumbnail());
        return metadata;
        
    }
}

