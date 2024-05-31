package dev.langchain4j.web.search.searchapi;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.time.Duration.ofSeconds;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
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
        final SearchApiResponse searchapiResponse = searchapiClient.search(request);

        // TODO:
        // outline the shape of the different types of JSON returned
        // use the json.method()s to map elements from each of the shapes to the 3 WebSearch* classes
        
        final JsonObject json = searchapiResponse.getJson();
        
//        final Optional<String> firstKey = json.keySet().stream().findFirst();
//        if (firstKey.isPresent()) {
//            final String key = firstKey.get();
//            System.out.println("********************************************************************");
//            if ("search_metadata".equalsIgnoreCase(key)) {
//            	System.out.println("**********************TRUE**********************************************");
//
//            } else if (true) {
//            	
//            } else {
//            	
//            }
//        }
        
        final Map<String, Object> searchParamsAndInfo = new HashMap<>();
        final Map<String, Object> searchMetadata = new HashMap<>();

        
        Map<String, JsonElement> _searchMetadata = null;
        if (json.has("search_metadata")) {
        	_searchMetadata = ((JsonObject)json.get("search_metadata")).getAsJsonObject().asMap();
            searchMetadata.putAll(_searchMetadata);
        }
        
        Map<String, JsonElement> _searchParamsAndInfo = null;
        Long totalResults = null;
        if (json.has("search_information")) {
        	_searchParamsAndInfo = ((JsonObject)json.get("search_information")).getAsJsonObject().asMap();
        	totalResults = _searchParamsAndInfo.get("total_results").getAsLong();
        	searchParamsAndInfo.putAll(_searchParamsAndInfo);
        }

        if (json.has("search_parameters")) {
        	_searchParamsAndInfo = ((JsonObject)json.get("search_parameters")).getAsJsonObject().asMap();
        	searchParamsAndInfo.putAll(_searchParamsAndInfo);
        }
        
        Integer pageNumber = null;
        if (json.has("pagination")) {
            pageNumber = ((JsonObject)json.get("pagination")).get("current").getAsInt();        	
        }
        
        
        final List<WebSearchOrganicResult> results = new ArrayList<>();
        if (json.has("organic_results")) {
        	final JsonArray _organicResults = json.getAsJsonArray("organic_results");
        	for (int i = 0; i < _organicResults.size(); i++) {
        		final JsonObject _obj = (JsonObject)_organicResults.get(i);
        		final String snippet = _obj.has("snippet") ? _obj.get("snippet").getAsString() : _obj.get("displayed_link").getAsString();
        		final WebSearchOrganicResult _objResult = WebSearchOrganicResult.from(
        				_obj.get("title").getAsString(), 
        				URI.create(_obj.get("link").getAsString()),
        				snippet,
        				null
        				);
        		results.add(i, _objResult);
        	}
        }
        
    	final WebSearchInformationResult result = WebSearchInformationResult.from(
    			(Long) getOrDefault(totalResults, results.size()), 
    			getOrDefault(pageNumber, 1), 
    			searchParamsAndInfo);
        return WebSearchResults.from(searchMetadata, result, results);
    }

    public static SearchApiWebSearchEngineBuilder withApiKey(final String apiKey) {
        return builder().apiKey(apiKey);
    }
}

