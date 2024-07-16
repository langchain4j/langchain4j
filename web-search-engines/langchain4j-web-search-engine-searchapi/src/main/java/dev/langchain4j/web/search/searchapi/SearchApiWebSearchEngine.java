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
import java.util.function.Consumer;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import dev.langchain4j.web.search.searchapi.result.OrganicResult;
import lombok.Builder;
import lombok.Getter;

/**
 * Represents SearchApi Search API as a {@code WebSearchEngine}. See more
 * details <a href="https://www.searchapi.io/docs/google">here</a>.
 */
public class SearchApiWebSearchEngine implements WebSearchEngine {
	public static final String DEFAULT_BASE_URL = "https://www.searchapi.io";
	public static final String DEFAULT_ENGINE = "google";
	private static final Integer DEFAULT_MAX_RESULTS = 5;

	protected final String apiKey;
	protected final String engine;
	protected final SearchApiClient searchapiClient;
	protected final Boolean logRequests;
	
    /**
     * Functional interface to allow customization of request parameters prior to a search request;
     */
	protected final Consumer<Map<String, Object>> customizeParametersFunc;

	@Builder
	public SearchApiWebSearchEngine(final String apiKey, final String engine, final Duration timeout,
			final Boolean logRequests, final Consumer<Map<String, Object>> customizeParametersFunc) {

		this.apiKey = ensureNotBlank(apiKey, "apiKey");
		this.engine = getOrDefault(engine, DEFAULT_ENGINE);
		this.logRequests = getOrDefault(logRequests, false);
		this.customizeParametersFunc = customizeParametersFunc;

		this.searchapiClient = SearchApiClient.builder().baseUrl(DEFAULT_BASE_URL).apiKey(this.apiKey)
				.engine(this.engine).timeout(getOrDefault(timeout, ofSeconds(30))).logRequests(this.logRequests)
				.build();
	}
	
	public SearchApiWebSearchEngine(final String apiKey, final String engine) {
		this(apiKey, engine, null, false, null);
	}
	
	public SearchApiWebSearchEngine(final String apiKey, final String engine, final Consumer<Map<String, Object>> customizeParametersFunc) {
		this(apiKey, engine, null, false, customizeParametersFunc);
	}	

	@Override
	public WebSearchResults search(final WebSearchRequest webSearchRequest) {
		ensureNotNull(webSearchRequest, "webSearchRequest");

		final SearchApiRequest.SearchApiRequestBuilder requestBuilder = SearchApiRequest.builder();
		
		requestBuilder.q(webSearchRequest.searchTerms())
		        .safe(webSearchRequest.safeSearch())
				.num(getOrDefault(webSearchRequest.maxResults(), DEFAULT_MAX_RESULTS)) // maxResults should default to 5
				.page(webSearchRequest.startPage());
		
        final SearchApiRequest request = requestBuilder.build();

		// future search engines must either: 
        // 1. call this method to customize the search parameters
        customizeSearchRequest(request, webSearchRequest);
        
        // 2. or use the functional interface
        if (customizeParametersFunc != null) {
        	customizeParametersFunc.accept(request.getParams());
        }

        // conduct the search
        final SearchApiResponse searchapiResponse = searchapiClient.search(request);
        
        // structure the results
        final List<WebSearchOrganicResult> results = searchapiResponse.getOrganicResults().stream()
				.map(result -> WebSearchOrganicResult.from(result.getTitle(), URI.create(result.getLink()),
						getOrDefault(result.getSnippet(), ""), // for the first few runs of the query I tried, "snippet" was null
						null, // searchapi does not return "content"
						toResultMetadataMap(result)))
				.collect(toList());

		// if the search query has a direct answer, append it to the top of the organic results
		customizeSearchResults(searchapiResponse, results);

//		final Long totalResults = Double
//				.valueOf(searchapiResponse.getSearchInformation().get("total_results").toString()).longValue();
		final WebSearchInformationResult result = WebSearchInformationResult.from(
//				getOrDefault(totalResults, Long.valueOf(results.size())),
				Long.valueOf(results.size()),
				getOrDefault(searchapiResponse.getPagination().getCurrent(), 1),
				searchapiResponse.getSearchParameters());

		// merge the "search_information" JSON element with the "search_metadata" JSON element, if present
//		searchapiResponse.getSearchMetadata().putAll(searchapiResponse.getSearchInformation());
		return WebSearchResults.from(searchapiResponse.getSearchMetadata(), result, results);
	}
	
	/**
	 * This life cycle method is always called before a search is performed by <code>WebSearchEngine#search</code>
	 * 
	 * Future search engines must call this method to customize the search request parameters. 
	 * Subclasses should use this method to pass additional key-value pairs to <code>SearchApiRequest#params</code>.
	 * These additional key-value pairs will be appended to the search query as custom parameters.
	 * 
	 * @param request
	 * @param webSearchRequest
	 */
	protected void customizeSearchRequest(final SearchApiRequest request, final WebSearchRequest webSearchRequest) {
		
		if (DEFAULT_ENGINE.equalsIgnoreCase(this.engine)) {
			final Map<String, Object> params = addDefaultSearchParameters(request);
			final Locale locale = new Locale(webSearchRequest.language());
	        params.put("hl", locale.hl);
	        params.put("gl", locale.gl);
		}
	}

    /**
     * This life cycle method is always called after a search is performed by <code>WebSearchEngine#search</code> 
     * but before the results are returned to the caller.
     * 
     * Future search engines must call this method to customize the search results. 
	 * Subclasses should use this method to apply transformations to the <code>List<WebSearchOrganicResult> results<code> 
	 * prior to it being sent to the caller.
     * 
     * @param response
     * @param results
     */
	@SuppressWarnings("unchecked")
	protected void customizeSearchResults(final SearchApiResponse response, final List<WebSearchOrganicResult> results) {
		
		if (!DEFAULT_ENGINE.equalsIgnoreCase(this.engine)) {
			return;
		}
		// For the Google Search engine, searchapi.io may include 1 or more optional JSON elements in the response
		// that directly answers the search query in the "knowledge_graph",
		// "answer_box", etc
		// such optional elements counts toward the "num" parameter. For instance, if
		// "num" is set to 5 and an "inline_images" is included, then "organic_results"
		// will contain 4 (instead of 5) items,
		// which is why we have to add it back to "results" below as the first item.
		// See also:
		// https://api.python.langchain.com/en/latest/_modules/langchain_community/utilities/searchapi.html#SearchApiAPIWrapper

		String title = null;
		String website = DEFAULT_BASE_URL;
		String description = null;

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
			website = response.getKnowledgeGraph().containsKey("website")
					? (String) response.getKnowledgeGraph().get("website")
					: website;
			description = (String) response.getKnowledgeGraph().get("description");

		} else if (response.getInlineVideos() != null && !response.getInlineVideos().isEmpty()) {
			final Map<String, Object> video = response.getInlineVideos().get(0);
			title = (String) video.get("title");
			website = video.containsKey("link") ? (String) video.get("link") : website;
			description = (String) video.get("image");

		} else if (response.getInlineImages() != null) {
			final Map<String, Object> image = (Map<String, Object>) ((List<Map<String, Object>>) response
					.getInlineImages().get("images")).get(0);
			title = (String) image.get("title");
			website = image.containsKey("source") ? (String) ((Map<String, Object>) image.get("source")).get("link")
					: website;
			description = (String) image.get("thumbnail");
		}

		if (title != null && website != null) {
			final OrganicResult result = new OrganicResult();
			result.setPosition(0);
			result.setSource("inline");
			results.add(0, WebSearchOrganicResult.from(title, URI.create(website), description, null,
					toResultMetadataMap(result)));
		}
	}
	
	public static SearchApiWebSearchEngine withApiKey(final String apiKey) {
		return builder().apiKey(apiKey).build();
	}

	private static Map<String, String> toResultMetadataMap(final OrganicResult result) {
		final Map<String, String> metadata = new HashMap<>();

		metadata.put("position", String.valueOf(result.getPosition()));
		return metadata;
	}
	
    /**
     * Returns a map of key-value pairs representing optional parameters for the Google search engine.
     *
     * @return A map containing the optional parameters and their values.
     */
    private Map<String, Object> addDefaultSearchParameters(final SearchApiRequest request) {
    	final Map<String, Object> params = request.getParams();

        if (request.isSafe())
            params.put("safe", "active");
        if (isValidInteger(request.getNum()))
            params.put("num", request.getNum());
        if (isValidInteger(request.getPage()))
            params.put("page", request.getPage());

        return params;
    }
    
    /**
     * Checks if the given Integer value is valid (not null and non-negative).
     *
     * @param value The Integer value to check.
     * @return true if the value is valid, false otherwise.
     */
    public static boolean isValidInteger(final Integer value) {
        return value != null && value.intValue() >= 0;
    }
    
    @Getter
	static class Locale {
        /**
         * 
         * Note that <code>hl</code> & <code>gl</code> default to <code>hl="en"</code> and <code>gl="us"</code>.
         * When concatenated they become the locale string "en-us".
         *
         */    	
		private final String hl;
		private final String gl;
		/**
		 * {@link WebSearchRequest#language} is in a composite string "en-us" for a locale but
		 * SearchApi splits them into 2 individual strings.
		 * See https://github.com/dewitt/opensearch/blob/master/opensearch-1-1-draft-6.md#the-language-element
		 */
		Locale(String language) {
			String hl = null;
			String gl = null;
			if (isNotNullOrBlank(language)) {
				language = (language.indexOf("_") != -1) ? language.replace("_", "-"): language;
				final String lang[] = language.split("-");
				if (lang.length == 2) {
					hl = lang[0].trim().toLowerCase();
					gl = lang[1].trim().toLowerCase();
				}
			}
			this.hl = getOrDefault(hl, "en");
			this.gl = getOrDefault(gl, "us");
		}
	}
}
