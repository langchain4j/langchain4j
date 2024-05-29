package dev.langchain4j.web.search.searchapi;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchResults;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "SEARCHAPI_API_KEY", matches = ".+")
class SearchApiWebSearchEngineTest {
	
	private static final boolean logRequests = true;
	
    @Test
    void should_search_with_result_1() {
    	try {

        // given
        SearchApiWebSearchEngine searchapiWebSearchEngine = SearchApiWebSearchEngine.builder()
                .apiKey(System.getenv("SEARCHAPI_API_KEY"))
                .logRequests(logRequests)
                .build();

        // when
        WebSearchResults webSearchResults = searchapiWebSearchEngine.search("What is LangChain4j?");
        
    	} catch (RuntimeException ignore) {
    		throw ignore;
    	}

        // then
//        List<WebSearchOrganicResult> results = webSearchResults.results();
//
//        results.forEach(result -> {
//            assertThat(result.title()).isNotBlank();
//            assertThat(result.url()).isNotNull();
//            assertThat(result.snippet()).isNotBlank();
//            assertThat(result.content()).isNotBlank();
////            assertThat(result.metadata()).containsOnlyKeys("score");
//        });
//
//        assertThat(results).anyMatch(result ->
//                result.url().toString().contains("https://github.com/langchain4j")
//                        && result.content().contains("How to get an API key")
//        );
    }
}