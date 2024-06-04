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
	public static final String DEFAULT_ENV_VAR = "SEARCHAPI_API_KEY";
    public static final String DEFAULT_BASE_URL = "https://www.searchapi.io";
    public static final String DEFAULT_ENGINE = "google";
    private static final Integer DEFAULT_MAX_RESULTS = 5;

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

        final SearchApiRequest.SearchApiRequestBuilder requestBuilder = SearchApiRequest.builder();
        
        requestBuilder
        	.q(webSearchRequest.searchTerms())
        	.safe(webSearchRequest.safeSearch())
            .num(getOrDefault(webSearchRequest.maxResults(), DEFAULT_MAX_RESULTS)) // maxResults should default to 5
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
        final SearchApiRequest request = requestBuilder.build();
    	
        
        // conduct the search
        final SearchApiResponse searchapiResponse = searchapiClient.search(request);        
        
        final List<WebSearchOrganicResult> results = searchapiResponse.getOrganicResults().stream()
                .map(result -> WebSearchOrganicResult.from(
                        result.getTitle(),
                        URI.create(result.getLink()),
                        result.getSnippet() != null ? result.getSnippet(): result.getDisplayedLink(), // for the first few runs of the query I tried, "snippet" is somehow null
                        null, // searchapi does not return "content"
                        toResultMetadataMap(result))) 
                .collect(toList());

        // if the search query has a direct answer, append it to the top of the organic results
        appendAnswerToResults(searchapiResponse, results);
        
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
    
    private static Map<String, String> toResultMetadataMap(final OrganicResult result) {
        final Map<String, String> metadata = new HashMap<>();
       
        metadata.put("position", String.valueOf(result.getPosition()));
        metadata.put("source", result.getSource());
        metadata.put("thumbnail", result.getThumbnail());
        return metadata;
    }
    
    @SuppressWarnings("unchecked")
	private static void appendAnswerToResults(final SearchApiResponse response, final List<WebSearchOrganicResult> results) {
    	// searchapi.io may include 1 or more optional JSON elements in the response that directly answers the search query in the "knowledge_graph", "answer_box", etc
        // such optional elements counts toward the "num" parameter. For instance, if "num" is set to 5 and an "inline_images" is included, then "organic_results" will contain 4 (instead of 5) items,  
        // which is why we have to add it back to "results" below as the first item.
    	// See also: https://api.python.langchain.com/en/latest/_modules/langchain_community/utilities/searchapi.html#SearchApiAPIWrapper
    	
    	String title = null, website = "https://www.searchapi.io/", description = null;
        
    	if (response.getAnswerBox() != null && !response.getAnswerBox().isEmpty()) {
        	title = (String) response.getAnswerBox().get("title");
        	website = (String) response.getAnswerBox().get("link");

    		if (response.getAnswerBox().containsKey("answer")) {
    			description = (String) response.getAnswerBox().get("answer");
    			
    		} else if (response.getAnswerBox().containsKey("snippet")) {
    			description = (String) response.getAnswerBox().get("snippet");
    		}
        } else if (response.getKnowledgeGraph() != null && !response.getKnowledgeGraph().isEmpty()) {
        	title = (String) response.getKnowledgeGraph().get("title");
        	website = response.getKnowledgeGraph().containsKey("website") ? (String) response.getKnowledgeGraph().get("website") : website;
        	description = (String) response.getKnowledgeGraph().get("description");
        	
        } else if (response.getInlineVideos() != null && !response.getInlineVideos().isEmpty()) {
        	final Map<String, Object> video = response.getInlineVideos().get(0);
        	title = (String) video.get("title");
        	website = video.containsKey("link") ? (String) video.get("link") : website;
        	description = (String) video.get("image");
    		
    	} else if (response.getInlineImages() != null) {
    		final Map<String, Object> image = (Map<String, Object>) ((List<Map<String, Object>>) response.getInlineImages().get("images")).get(0);
    		title = (String) image.get("title");
    		website = image.containsKey("source") ? (String) ((Map<String, Object>) image.get("source")).get("link") : website;
        	description = (String) image.get("thumbnail");    		
    	}
    	

        if (title !=  null && website != null) {
        	OrganicResult result = new OrganicResult();
        	result.setPosition(0); result.setSource("inline");
        	results.add(0, WebSearchOrganicResult.from(title, URI.create(website), description, null, toResultMetadataMap(result)));
        }
    }
}

