package dev.langchain4j.web.search.searchapi;

import static dev.langchain4j.web.search.searchapi.SearchApiWebSearchEngine.DEFAULT_ENGINE;
import static dev.langchain4j.web.search.searchapi.SearchApiWebSearchEngine.DEFAULT_ENV_VAR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchResults;

@EnabledIfEnvironmentVariable(named = "SEARCHAPI_API_KEY", matches = ".+")
class SearchApiWebSearchEngineIT extends WebSearchEngineIT {

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
		assertThat(searchMetadata).containsEntry("query_displayed", "chatgpt").containsEntry("status", "Success");

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

	@Override
	protected WebSearchEngine searchEngine() {
		return webSearchEngine;
	}
}