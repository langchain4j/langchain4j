package dev.langchain4j.web.search.searchapi;

import static dev.langchain4j.web.search.searchapi.SearchApiWebSearchEngine.DEFAULT_ENGINE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;

@EnabledIfEnvironmentVariable(named = "SEARCHAPI_API_KEY", matches = ".+")
class SearchApiWebSearchEngineIT extends WebSearchEngineIT {
	public static final String DEFAULT_ENV_VAR = "SEARCHAPI_API_KEY";

	private static final boolean logRequests = false;

	protected WebSearchEngine webSearchEngine = SearchApiWebSearchEngine.builder()
			.apiKey(System.getenv(DEFAULT_ENV_VAR))
			.logRequests(logRequests).build();

	@Test
	void search_for_chatgpt() {
		// given
		SearchApiWebSearchEngine searchapiWebSearchEngine = SearchApiWebSearchEngine.builder()
				.apiKey(System.getenv(DEFAULT_ENV_VAR)).logRequests(logRequests).build();

		// when
		WebSearchResults webSearchResults = searchapiWebSearchEngine.search("chatgpt");

		// then
		assertThat(webSearchResults.searchInformation().totalResults()).isPositive();
		assertThat(webSearchResults.searchInformation().pageNumber()).isEqualTo(1);

		// then
		WebSearchInformationResult searchParams = webSearchResults.searchInformation();
		assertThat(searchParams.metadata()).containsEntry("engine", DEFAULT_ENGINE);
		assertThat(searchParams.metadata()).containsEntry("q", "chatgpt");
		assertThat(searchParams.metadata()).containsEntry("google_domain", "google.com");
		assertThat(searchParams.metadata()).containsEntry("device", "desktop");
		assertThat(searchParams.metadata()).containsEntry("safe", "active");
		assertThat(searchParams.metadata()).containsEntry("page", "1");

		// then
		Map<String, Object> searchMetadata = webSearchResults.searchMetadata();
		assertThat(searchMetadata).containsKey("id");
		assertThat(searchMetadata.get("id")).isNotNull();
		assertThat(searchMetadata).containsKey("created_at");
		assertThat(searchMetadata.get("created_at")).isNotNull();
		assertThat(searchMetadata).containsKey("request_url");
		assertThat(searchMetadata.get("request_url")).isNotNull();

		// then
		List<WebSearchOrganicResult> results = webSearchResults.results();

		results.forEach(result -> {
			assertThat(result.title()).isNotBlank();
			assertThat(result.url()).isNotNull();
			assertThat(result.snippet()).isNotBlank();
			assertThat(result.content()).isNull();
			assertThat(result.metadata()).isNotNull();
		});

		assertThat(results).anyMatch(result -> result.url().toString().contains("https://openai.com/chatgpt/")
				&& (result.snippet().contains("Free to use") // when "snippet" != null
						|| result.snippet().contains("ChatGPT")) // when "displayedLink" != null
		);
	}
	
	@Test
	void customize_google_search_parameters1() {
		// given
		SearchApiWebSearchEngine searchapiWebSearchEngine = new SearchApiWebSearchEngine(System.getenv(DEFAULT_ENV_VAR), DEFAULT_ENGINE) {
			@Override
			protected void customizeSearchRequest(final SearchApiRequest request, final WebSearchRequest webSearchRequest) {
				super.customizeSearchRequest(request, webSearchRequest);
				request.getParams().put("device", "mobile");
				request.getParams().put("google_domain", "google.co.uk");
				request.getParams().put("safe", "off");
			}
		};

		// when
		WebSearchResults webSearchResults = searchapiWebSearchEngine.search("chatgpt");

		// then
		assertThat(webSearchResults.searchInformation().totalResults()).isPositive();
		assertThat(webSearchResults.searchInformation().pageNumber()).isEqualTo(1);

		// then
		WebSearchInformationResult searchParams = webSearchResults.searchInformation();
		assertThat(searchParams.metadata()).containsEntry("engine", DEFAULT_ENGINE);
		assertThat(searchParams.metadata()).containsEntry("q", "chatgpt");
		assertThat(searchParams.metadata()).containsEntry("google_domain", "google.co.uk");
		assertThat(searchParams.metadata()).containsEntry("device", "mobile");
		assertThat(searchParams.metadata()).containsEntry("safe", "off");
		assertThat(searchParams.metadata()).containsEntry("page", "1");

		// then
		Map<String, Object> searchMetadata = webSearchResults.searchMetadata();
		assertThat(searchMetadata).containsKey("id");
		assertThat(searchMetadata.get("id")).isNotNull();
		assertThat(searchMetadata).containsKey("created_at");
		assertThat(searchMetadata.get("created_at")).isNotNull();
		assertThat(searchMetadata).containsKey("request_url");
		assertThat(searchMetadata.get("request_url")).isNotNull();

		// then
		List<WebSearchOrganicResult> results = webSearchResults.results();

		results.forEach(result -> {
			assertThat(result.title()).isNotBlank();
			assertThat(result.url()).isNotNull();
			assertThat(result.snippet()).isNotBlank();
			assertThat(result.content()).isNull();
			assertThat(result.metadata()).isNotNull();
		});
	}

	@Test
	void customize_google_search_parameters2() {
		// given
		SearchApiWebSearchEngine searchapiWebSearchEngine = new SearchApiWebSearchEngine(
				System.getenv(DEFAULT_ENV_VAR), 
				DEFAULT_ENGINE,
				Duration.ofSeconds(10), 
				true,
				(params) -> {
					params.put("device", "mobile");
					params.put("google_domain", "google.co.uk");
					params.put("safe", "off");
				}
		);

		// when
		WebSearchResults webSearchResults = searchapiWebSearchEngine.search("chatgpt");

		// then
		assertThat(webSearchResults.searchInformation().totalResults()).isPositive();
		assertThat(webSearchResults.searchInformation().pageNumber()).isEqualTo(1);

		// then
		WebSearchInformationResult searchParams = webSearchResults.searchInformation();
		assertThat(searchParams.metadata()).containsEntry("engine", DEFAULT_ENGINE);
		assertThat(searchParams.metadata()).containsEntry("q", "chatgpt");
		assertThat(searchParams.metadata()).containsEntry("google_domain", "google.co.uk");
		assertThat(searchParams.metadata()).containsEntry("device", "mobile");
		assertThat(searchParams.metadata()).containsEntry("safe", "off");
		assertThat(searchParams.metadata()).containsEntry("page", "1");

		// then
		Map<String, Object> searchMetadata = webSearchResults.searchMetadata();
		assertThat(searchMetadata).containsKey("id");
		assertThat(searchMetadata.get("id")).isNotNull();
		assertThat(searchMetadata).containsKey("created_at");
		assertThat(searchMetadata.get("created_at")).isNotNull();
		assertThat(searchMetadata).containsKey("request_url");
		assertThat(searchMetadata.get("request_url")).isNotNull();

		// then
		List<WebSearchOrganicResult> results = webSearchResults.results();

		results.forEach(result -> {
			assertThat(result.title()).isNotBlank();
			assertThat(result.url()).isNotNull();
			assertThat(result.snippet()).isNotBlank();
			assertThat(result.content()).isNull();
			assertThat(result.metadata()).isNotNull();
		});
	}
	
	@Test
	void customize_bing_search_parameters() {
		// given
		SearchApiWebSearchEngine searchapiWebSearchEngine = new SearchApiWebSearchEngine(System.getenv(DEFAULT_ENV_VAR), "bing") {
			@Override
			protected void customizeSearchRequest(final SearchApiRequest request, final WebSearchRequest webSearchRequest) {
				request.getParams().put("device", "tablet");
				request.getParams().put("language", "en");
				request.getParams().put("safe_search", "strict");
				request.getParams().put("num", "10");
				request.getParams().put("page", "1");
			}
		};

		// when
		WebSearchResults webSearchResults = searchapiWebSearchEngine.search("chatgpt");

		// then
		assertThat(webSearchResults.searchInformation().totalResults()).isPositive();
		assertThat(webSearchResults.searchInformation().pageNumber()).isEqualTo(1);

		// then
		WebSearchInformationResult searchParams = webSearchResults.searchInformation();
		assertThat(searchParams.metadata()).containsEntry("engine", "bing");
		assertThat(searchParams.metadata()).containsEntry("q", "chatgpt");
		assertThat(searchParams.metadata()).containsEntry("device", "tablet");
		assertThat(searchParams.metadata()).containsEntry("language", "en");
		assertThat(searchParams.metadata()).containsEntry("safe_search", "strict");
		assertThat(searchParams.metadata()).containsEntry("page", "1");

		// then
		Map<String, Object> searchMetadata = webSearchResults.searchMetadata();
		assertThat(searchMetadata).containsKey("id");
		assertThat(searchMetadata.get("id")).isNotNull();
		assertThat(searchMetadata).containsKey("created_at");
		assertThat(searchMetadata.get("created_at")).isNotNull();
		assertThat(searchMetadata).containsKey("request_url");
		assertThat(searchMetadata.get("request_url")).isNotNull();

		// then
		List<WebSearchOrganicResult> results = webSearchResults.results();
		
		assertThat(results).isNotEmpty();

		results.forEach(result -> {
			assertThat(result.title()).isNotBlank();
			assertThat(result.url()).isNotNull();
			assertThat(result.snippet()).isNotBlank();
			assertThat(result.content()).isNull();
			assertThat(result.metadata()).isNotNull();
		});
	}

	@Test
	void customize_baidu_search_parameters() {
		// given
		SearchApiWebSearchEngine searchapiWebSearchEngine = SearchApiWebSearchEngine.builder()
				.apiKey(System.getenv(DEFAULT_ENV_VAR))
				.engine("baidu")
				.logRequests(true)
				.customizeParametersFunc(
						(params) -> {
							params.put("ct", "0");
							params.put("num", "5");
							params.put("page", "1");
						})
				.build();

		// when
		WebSearchResults webSearchResults = searchapiWebSearchEngine.search("chatgpt");

		// then
		assertThat(webSearchResults.searchInformation().totalResults()).isPositive();
		assertThat(webSearchResults.searchInformation().pageNumber()).isEqualTo(1);

		// then
		WebSearchInformationResult searchParams = webSearchResults.searchInformation();
		assertThat(searchParams.metadata()).containsEntry("engine", "baidu");
		assertThat(searchParams.metadata()).containsEntry("q", "chatgpt");
		assertThat(searchParams.metadata()).containsEntry("ct", "0");
		assertThat(searchParams.metadata()).containsEntry("num", "5");
		assertThat(searchParams.metadata()).containsEntry("page", "1");

		// then
		Map<String, Object> searchMetadata = webSearchResults.searchMetadata();
		assertThat(searchMetadata).containsKey("id");
		assertThat(searchMetadata.get("id")).isNotNull();
		assertThat(searchMetadata).containsKey("created_at");
		assertThat(searchMetadata.get("created_at")).isNotNull();
		assertThat(searchMetadata).containsKey("request_url");
		assertThat(searchMetadata.get("request_url")).isNotNull();

		// then
		List<WebSearchOrganicResult> results = webSearchResults.results();
		
		assertThat(results).isNotEmpty();

		results.forEach(result -> {
			assertThat(result.title()).isNotBlank();
			assertThat(result.url()).isNotNull();
			assertThat(result.snippet()).isNotNull();
			assertThat(result.content()).isNull();
			assertThat(result.metadata()).isNotNull();
		});
	}
	
//	@Test
//	void customize_youtube_transcripts_parameters() {
//		// given
//		SearchApiWebSearchEngine searchapiWebSearchEngine = new SearchApiWebSearchEngine(System.getenv(DEFAULT_ENV_VAR), "youtube_transcripts") {
//			@Override
//			protected void customizeSearchRequest(final SearchApiRequest request, final WebSearchRequest webSearchRequest) {
//				super.customizeSearchRequest(request, webSearchRequest);
//				request.getParams().put("video_id", "0e3GPea1Tyg");
//				request.getParams().put("lang", "en");
//			}
//		};
//
//		// when
//		WebSearchResults webSearchResults = searchapiWebSearchEngine.search("chatgpt");
//
//		// then
//		assertThat(webSearchResults.searchInformation().totalResults()).isPositive();
//		assertThat(webSearchResults.searchInformation().pageNumber()).isEqualTo(1);
//
//		// then
//		WebSearchInformationResult searchParams = webSearchResults.searchInformation();
//		assertThat(searchParams.metadata()).containsEntry("engine", "youtube_transcripts");
//		assertThat(searchParams.metadata()).containsEntry("video_id", "0e3GPea1Tyg");
//		assertThat(searchParams.metadata()).containsEntry("lang", "en");
//
//		// then
//		Map<String, Object> searchMetadata = webSearchResults.searchMetadata();
//		assertThat(searchMetadata).containsKey("id");
//		assertThat(searchMetadata.get("id")).isNotNull();
//		assertThat(searchMetadata).containsKey("created_at");
//		assertThat(searchMetadata.get("created_at")).isNotNull();
//		assertThat(searchMetadata).containsKey("request_url");
//		assertThat(searchMetadata.get("request_url")).isNotNull();
//
//		// then
//		List<WebSearchOrganicResult> results = webSearchResults.results();
//
//		results.forEach(result -> {
//			assertThat(result.title()).isNotBlank();
//			assertThat(result.url()).isNotNull();
//			assertThat(result.snippet()).isNotBlank();
//			assertThat(result.content()).isNull();
//			assertThat(result.metadata()).isNotNull();
//		});
//	}
	
	@Override
	protected WebSearchEngine searchEngine() {
		return webSearchEngine;
	}
}