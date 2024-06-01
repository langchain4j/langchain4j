package dev.langchain4j.web.search.searchapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchResults;

import static dev.langchain4j.web.search.searchapi.SearchApiWebSearchEngine.*;

@EnabledIfEnvironmentVariable(named = "SEARCHAPI_API_KEY", matches = ".+")
class SearchApiWebSearchEngineTest {
	
	private static final boolean logRequests = true;
	
    @Test
    void search_for_chatgpt() {
        // given
        SearchApiWebSearchEngine searchapiWebSearchEngine = SearchApiWebSearchEngine.builder()
                .apiKey(System.getenv("SEARCHAPI_API_KEY"))
                .logRequests(logRequests)
                .build();

        // when
        WebSearchResults webSearchResults = searchapiWebSearchEngine.search("chatgpt");
        
        // then
        Map<String, Object> searchMetadata = webSearchResults.searchMetadata();
        assertThat(searchMetadata.containsKey("id"));
        assertThat(searchMetadata.get("id")).isNotNull();
        assertThat(searchMetadata.containsKey("created_at"));
        assertThat(searchMetadata.get("created_at")).isNotNull();
        assertThat(searchMetadata.containsKey("request_url"));
        assertThat(searchMetadata.get("request_url")).isNotNull();
        assertThat(searchMetadata.containsKey("status"));
        assertThat(searchMetadata.get("status").equals("Success"));
        
        // then
        WebSearchInformationResult info = webSearchResults.searchInformation();
        assertThat(info.totalResults() > 0);
        assertThat(info.pageNumber() == 1);
        assertThat(info.metadata().containsKey("engine"));
        assertThat(info.metadata().get("engine").equals(DEFAULT_ENGINE));
        assertThat(info.metadata().containsKey("q"));
        assertThat(info.metadata().get("q").equals("chatgpt"));
        assertThat(info.metadata().containsKey("query_displayed"));
        assertThat(info.metadata().get("query_displayed").equals("chatgpt"));
        assertThat(info.metadata().containsKey("google_domain"));
        assertThat(info.metadata().get("google_domain").equals("google.com"));

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();

        results.forEach(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).isNotNull();
            assertThat(result.snippet()).isNotBlank();
            assertThat(result.content()).isBlank();
            assertThat(result.metadata()).containsOnlyKeys("thumbnail");
        });

        assertThat(results).anyMatch(result ->
                result.url().toString().contains("https://chat.openai.com/")
                        && result.snippet().contains("ChatGPT is a free-to-use AI system.")
        );
    }

/*    @Test
    void search_for_chatgpt() {
        // given
        SearchApiWebSearchEngine searchapiWebSearchEngine = SearchApiWebSearchEngine.builder()
                .apiKey(System.getenv("SEARCHAPI_API_KEY"))
                .logRequests(logRequests)
                .build();

        // when
        WebSearchResults webSearchResults = searchapiWebSearchEngine.search("chatgpt");
        // System.out.println("" + webSearchResults.toString());
        
        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();

        results.forEach(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).isNotNull();
            assertThat(result.snippet()).isNotBlank();
            assertThat(result.content()).isBlank();
            assertThat(result.metadata()).containsOnlyKeys("thumbnail");
        });

        assertThat(results).anyMatch(result ->
                result.url().toString().contains("https://chat.openai.com/")
                        && result.snippet().contains("ChatGPT is a free-to-use AI system.")
        );
    }*/
    
    
}