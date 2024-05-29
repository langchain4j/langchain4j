package dev.langchain4j.web.search.searchapi;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchResults;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "SEARCHAPI_API_KEY", matches = ".+")
class SearchApiWebSearchEngineIT extends WebSearchEngineIT {

	public static final String SEARCHAPI_API_KEY = "SEARCHAPI_API_KEY";
	
	private static final boolean logRequests = true;
	
    protected WebSearchEngine webSearchEngine = SearchApiWebSearchEngine.withApiKey(System.getenv(SEARCHAPI_API_KEY)).logRequests(logRequests).build();

    @Test
    void should_search_with_raw_content() {

        // given
        SearchApiWebSearchEngine searchapiWebSearchEngine = SearchApiWebSearchEngine.builder()
                .apiKey(System.getenv(SEARCHAPI_API_KEY))
                .logRequests(logRequests)
                .build();

        // when
        WebSearchResults webSearchResults = searchapiWebSearchEngine.search("What is LangChain4j?");

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();

        results.forEach(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).isNotNull();
            assertThat(result.snippet()).isNotBlank();
            assertThat(result.content()).isNotBlank();
        });

        assertThat(results).anyMatch(result ->
                result.url().toString().contains("https://github.com/langchain4j")
                        && result.content().contains("How to get an API key")
        );
    }

/*
    @Test
    void should_search_with_answer() {

        // given
        SearchApiWebSearchEngine searchapiWebSearchEngine = SearchApiWebSearchEngine.builder()
                .apiKey(System.getenv(SEARCHAPI_API_KEY))
                .logRequests(logRequests)
                .build();

        // when
        WebSearchResults webSearchResults = searchapiWebSearchEngine.search("What is LangChain4j?");

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSize(5 + 1); // +1 for answer

        WebSearchOrganicResult answerResult = results.get(0);
        assertThat(answerResult.title()).isEqualTo("SearchApi Search API");
        assertThat(answerResult.url()).isEqualTo(URI.create("https://searchapi.io/"));
        assertThat(answerResult.snippet()).isNotBlank();
        assertThat(answerResult.content()).isNull();
        assertThat(answerResult.metadata()).isNull();

        results.subList(1, results.size()).forEach(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).isNotNull();
            assertThat(result.snippet()).isNotBlank();
            assertThat(result.content()).isNull();
            assertThat(result.metadata()).containsOnlyKeys("score");
        });

        assertThat(results).anyMatch(result -> result.url().toString().contains("https://github.com/langchain4j"));
    }*/

    @Override
    protected WebSearchEngine searchEngine() {
        return webSearchEngine;
    }
}